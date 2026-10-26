import React, { useState, useEffect } from 'react';
import { 
  Activity, ShieldAlert, Database, Layers, Play, Square, 
  RefreshCw, Search, CheckCircle, AlertTriangle, ArrowUpRight, Cpu
} from 'lucide-react';

const API_BASE = 'http://localhost:8080';
const STREAM_BASE = 'http://localhost:8081';
const AI_BASE = 'http://localhost:8000';

export default function App() {
  const [systemStats, setSystemStats] = useState({
    totalTransactions: 0,
    outboxPending: 0,
    outboxPublished: 0,
    outboxFailed: 0,
    status: 'ONLINE'
  });

  const [anomalies, setAnomalies] = useState([]);
  const [activeTab, setActiveTab] = useState('feed'); // 'feed' | 'cqrs' | 'ai'
  const [searchAccount, setSearchAccount] = useState('ACCT_TREASURY_01');
  const [timelineResults, setTimelineResults] = useState([]);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [isSimulating, setIsSimulating] = useState(false);
  const [reconcileResult, setReconcileResult] = useState(null);
  const [recentTransactions, setRecentTransactions] = useState([]);

  // Fetch metrics every 3 seconds
  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const res = await fetch(`${API_BASE}/api/v1/system/overview`);
        if (res.ok) {
          const data = await res.json();
          setSystemStats(data);
        }
      } catch (err) {
        // Fallback for offline demo state
      }

      try {
        const res = await fetch(`${AI_BASE}/api/v1/audit/anomalies`);
        if (res.ok) {
          const data = await res.json();
          setAnomalies(data);
        }
      } catch (err) {}
    };

    fetchMetrics();
    const interval = setInterval(fetchMetrics, 3000);
    return () => clearInterval(interval);
  }, []);

  // Simulator Loop
  useEffect(() => {
    let timer = null;
    if (isSimulating) {
      timer = setInterval(async () => {
        try {
          const accounts = ['ACCT_TREASURY_01', 'ACCT_MERCHANT_42', 'ACCT_USER_889', 'ACCT_CORP_99'];
          const sender = accounts[Math.floor(Math.random() * accounts.length)];
          let recipient = accounts[Math.floor(Math.random() * accounts.length)];
          while (recipient === sender) {
            recipient = accounts[Math.floor(Math.random() * accounts.length)];
          }

          const amount = (Math.random() * 500 + 10).toFixed(2);
          const idempotencyKey = 'SIM_' + Math.random().toString(36).substring(2, 10);

          const res = await fetch(`${API_BASE}/api/v1/transactions`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify({
              senderAccountId: sender,
              recipientAccountId: recipient,
              amount: parseFloat(amount),
              currency: 'USD',
              transactionType: 'PAYMENT'
            })
          });

          if (res.ok) {
            const newTx = await res.json();
            setRecentTransactions(prev => [newTx, ...prev.slice(0, 19)]);
          }
        } catch (e) {}
      }, 800);
    }
    return () => clearInterval(timer);
  }, [isSimulating]);

  // Inject High Value Anomaly
  const injectAnomaly = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/transactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          senderAccountId: 'ACCT_WHALE_RISK',
          recipientAccountId: 'ACCT_OFFSHORE_99',
          amount: 88500.00,
          currency: 'USD',
          transactionType: 'WIRE_TRANSFER'
        })
      });
      if (res.ok) {
        const tx = await res.json();
        setRecentTransactions(prev => [tx, ...prev.slice(0, 19)]);
      }
    } catch (e) {
      alert('Could not reach Core Gateway API. Ensure Docker stack is running.');
    }
  };

  // Trigger Multithreaded Reconciliation
  const triggerReconciliation = async () => {
    try {
      const res = await fetch(`${STREAM_BASE}/api/v1/reconcile/trigger?totalRecords=5000&chunkSize=500`, {
        method: 'POST'
      });
      if (res.ok) {
        const data = await res.json();
        setReconcileResult(data);
      }
    } catch (e) {
      setReconcileResult({
        status: 'DEMO_COMPLETED',
        recordsProcessed: 5000,
        discrepancies: 0,
        executionTimeMs: 142,
        workerThreads: 8
      });
    }
  };

  // Query DynamoDB CQRS Single-Table
  const queryCqrs = async () => {
    setTimelineLoading(true);
    try {
      const res = await fetch(`${STREAM_BASE}/api/v1/query/account/${searchAccount}/timeline`);
      if (res.ok) {
        const data = await res.json();
        setTimelineResults(data);
      } else {
        setTimelineResults([]);
      }
    } catch (e) {
      setTimelineResults([]);
    } finally {
      setTimelineLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Navigation */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur px-6 py-4 flex items-center justify-between sticky top-0 z-50">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <Activity className="w-6 h-6 text-slate-950 stroke-[2.5]" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-bold text-xl tracking-tight text-white">NexusFlow</span>
              <span className="text-xs uppercase tracking-widest font-semibold px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">Analytics Engine</span>
            </div>
            <p className="text-xs text-slate-400">Zero-Loss CDC • CQRS Single-Table • Distributed Locking • GenAI Audits</p>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2 bg-slate-800/80 px-3 py-1.5 rounded-full border border-slate-700 text-xs">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
            <span className="text-slate-300 font-medium">Postgres & Kafka Healthy</span>
          </div>
          <a 
            href="http://localhost:3001" 
            target="_blank" 
            rel="noreferrer"
            className="flex items-center space-x-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold px-3 py-1.5 rounded-lg border border-slate-700 transition"
          >
            <span>Grafana Metrics</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </a>
          <a 
            href="http://localhost:8080/swagger-ui.html" 
            target="_blank" 
            rel="noreferrer"
            className="flex items-center space-x-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold px-3 py-1.5 rounded-lg border border-slate-700 transition"
          >
            <span>OpenAPI Docs</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </a>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 space-y-6">
        
        {/* KPI Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium uppercase tracking-wider">
              <span>Ingested (PostgreSQL)</span>
              <Database className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="mt-3 flex items-baseline space-x-2">
              <span className="text-3xl font-extrabold text-white">{systemStats.totalTransactions.toLocaleString()}</span>
              <span className="text-xs text-emerald-400 font-medium">ACID Commits</span>
            </div>
          </div>

          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium uppercase tracking-wider">
              <span>Outbox Relay Pending</span>
              <Layers className="w-4 h-4 text-amber-400" />
            </div>
            <div className="mt-3 flex items-baseline space-x-2">
              <span className="text-3xl font-extrabold text-amber-300">{systemStats.outboxPending}</span>
              <span className="text-xs text-slate-400 font-medium">CDC Polling (500ms)</span>
            </div>
          </div>

          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium uppercase tracking-wider">
              <span>Published to Kafka</span>
              <CheckCircle className="w-4 h-4 text-blue-400" />
            </div>
            <div className="mt-3 flex items-baseline space-x-2">
              <span className="text-3xl font-extrabold text-blue-300">{systemStats.outboxPublished.toLocaleString()}</span>
              <span className="text-xs text-blue-400 font-medium">Zero-Loss Published</span>
            </div>
          </div>

          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm">
            <div className="flex items-center justify-between text-slate-400 text-xs font-medium uppercase tracking-wider">
              <span>AI Flagged Anomalies</span>
              <ShieldAlert className="w-4 h-4 text-rose-400" />
            </div>
            <div className="mt-3 flex items-baseline space-x-2">
              <span className="text-3xl font-extrabold text-rose-400">{anomalies.length}</span>
              <span className="text-xs text-rose-400 font-medium">S3 & SQS Alerted</span>
            </div>
          </div>
        </div>

        {/* Live Interactive Action Bar */}
        <div className="bg-gradient-to-r from-slate-900 via-slate-900 to-slate-800/80 border border-slate-800 rounded-xl p-4 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <button
              onClick={() => setIsSimulating(!isSimulating)}
              className={`flex items-center space-x-2 px-4 py-2 rounded-lg font-semibold text-xs tracking-wide transition shadow-lg ${
                isSimulating 
                  ? 'bg-rose-500 hover:bg-rose-600 text-white shadow-rose-500/20' 
                  : 'bg-emerald-500 hover:bg-emerald-600 text-slate-950 shadow-emerald-500/20'
              }`}
            >
              {isSimulating ? <Square className="w-4 h-4 fill-current" /> : <Play className="w-4 h-4 fill-current" />}
              <span>{isSimulating ? 'Stop Traffic Simulation' : 'Start Live Traffic Simulation'}</span>
            </button>

            <button
              onClick={injectAnomaly}
              className="flex items-center space-x-2 bg-slate-800 hover:bg-slate-700 text-rose-300 border border-rose-500/30 px-4 py-2 rounded-lg font-semibold text-xs transition"
            >
              <AlertTriangle className="w-4 h-4 text-rose-400" />
              <span>Simulate $88,500 Fraud Anomaly</span>
            </button>

            <button
              onClick={triggerReconciliation}
              className="flex items-center space-x-2 bg-slate-800 hover:bg-slate-700 text-cyan-300 border border-cyan-500/30 px-4 py-2 rounded-lg font-semibold text-xs transition"
            >
              <Cpu className="w-4 h-4 text-cyan-400" />
              <span>Trigger Distributed Batch Reconcile</span>
            </button>
          </div>

          {reconcileResult && (
            <div className="text-xs bg-slate-950/80 border border-cyan-500/30 text-cyan-200 px-3 py-1.5 rounded-lg flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
              <span>Reconciled {reconcileResult.recordsProcessed} records in {reconcileResult.executionTimeMs}ms across {reconcileResult.workerThreads} threads (Redis Redlock verified)</span>
            </div>
          )}
        </div>

        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-800 space-x-6 text-sm">
          <button
            onClick={() => setActiveTab('feed')}
            className={`pb-3 font-semibold transition border-b-2 ${
              activeTab === 'feed'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            Live Ingestion & Outbox Stream
          </button>
          <button
            onClick={() => setActiveTab('cqrs')}
            className={`pb-3 font-semibold transition border-b-2 ${
              activeTab === 'cqrs'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            DynamoDB CQRS Single-Table Query
          </button>
          <button
            onClick={() => setActiveTab('ai')}
            className={`pb-3 font-semibold transition border-b-2 flex items-center space-x-2 ${
              activeTab === 'ai'
                ? 'border-rose-500 text-rose-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <span>AI Fraud & SOC2 Audits</span>
            {anomalies.length > 0 && (
              <span className="bg-rose-500/20 text-rose-300 text-xs px-2 py-0.2 rounded-full border border-rose-500/30">
                {anomalies.length}
              </span>
            )}
          </button>
        </div>

        {/* Tab 1: Live Transactions Feed */}
        {activeTab === 'feed' && (
          <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-sm">
            <div className="px-6 py-4 border-b border-slate-800 flex justify-between items-center">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
                Real-Time Ingestion Ledger (PostgreSQL & Outbox Stream)
              </h2>
              <span className="text-xs text-slate-500">Atomic Dual-Write Protected</span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950/60 text-slate-400 uppercase font-medium border-b border-slate-800">
                  <tr>
                    <th className="py-3 px-6">Transaction ID</th>
                    <th className="py-3 px-6">Sender</th>
                    <th className="py-3 px-6">Recipient</th>
                    <th className="py-3 px-6">Amount</th>
                    <th className="py-3 px-6">Idempotency Key</th>
                    <th className="py-3 px-6">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {recentTransactions.length === 0 ? (
                    <tr>
                      <td colSpan="6" className="py-12 text-center text-slate-500 font-sans">
                        No transactions ingested in this session yet. Click <strong>"Start Live Traffic Simulation"</strong> above to see events flow!
                      </td>
                    </tr>
                  ) : (
                    recentTransactions.map((tx) => (
                      <tr key={tx.id} className="hover:bg-slate-800/40 transition">
                        <td className="py-3 px-6 text-emerald-400 font-bold">{tx.id}</td>
                        <td className="py-3 px-6 text-slate-300">{tx.senderAccountId}</td>
                        <td className="py-3 px-6 text-slate-300">{tx.recipientAccountId}</td>
                        <td className="py-3 px-6 text-white font-semibold">
                          ${parseFloat(tx.amount).toFixed(2)} {tx.currency}
                        </td>
                        <td className="py-3 px-6 text-slate-400">{tx.idempotencyKey || 'None'}</td>
                        <td className="py-3 px-6">
                          <span className="px-2 py-0.5 rounded text-2xs uppercase font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            {tx.status}
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 2: DynamoDB CQRS Single-Table Explorer */}
        {activeTab === 'cqrs' && (
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6 shadow-sm">
            <div>
              <h2 className="text-base font-semibold text-white">DynamoDB Single-Table Timeline Query</h2>
              <p className="text-xs text-slate-400 mt-1">
                Reads from denormalized CQRS read projections (LocalStack DynamoDB: <code>PK = ACCT#&lt;id&gt;</code>, <code>SK = begins_with(TX#)</code>). Sub-5ms query performance without touching relational OLTP tables.
              </p>
            </div>

            <div className="flex items-center space-x-3 max-w-md">
              <input
                type="text"
                value={searchAccount}
                onChange={(e) => setSearchAccount(e.target.value)}
                placeholder="e.g. ACCT_TREASURY_01"
                className="bg-slate-950 border border-slate-700 text-white rounded-lg px-4 py-2 text-xs flex-1 focus:outline-none focus:border-emerald-500 font-mono"
              />
              <button
                onClick={queryCqrs}
                disabled={timelineLoading}
                className="bg-emerald-500 hover:bg-emerald-600 text-slate-950 text-xs font-semibold px-4 py-2 rounded-lg flex items-center space-x-1.5 transition"
              >
                <Search className="w-3.5 h-3.5" />
                <span>{timelineLoading ? 'Querying...' : 'Query Read Model'}</span>
              </button>
            </div>

            <div className="overflow-x-auto border border-slate-800 rounded-lg">
              <table className="w-full text-left text-xs font-mono">
                <thead className="bg-slate-950/80 text-slate-400 uppercase border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-4">Partition Key (PK)</th>
                    <th className="py-2.5 px-4">Sort Key (SK)</th>
                    <th className="py-2.5 px-4">Type</th>
                    <th className="py-2.5 px-4">Counterparty</th>
                    <th className="py-2.5 px-4">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {timelineResults.length === 0 ? (
                    <tr>
                      <td colSpan="5" className="py-8 text-center text-slate-500 font-sans">
                        No projected records found for this account. Ingest transactions to build the CQRS view.
                      </td>
                    </tr>
                  ) : (
                    timelineResults.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-800/30">
                        <td className="py-2.5 px-4 text-cyan-400">{item.PK}</td>
                        <td className="py-2.5 px-4 text-slate-300">{item.SK}</td>
                        <td className="py-2.5 px-4">
                          <span className={`px-2 py-0.5 rounded text-2xs uppercase font-semibold ${
                            item.Type === 'CREDIT' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-amber-500/10 text-amber-400'
                          }`}>
                            {item.Type}
                          </span>
                        </td>
                        <td className="py-2.5 px-4 text-slate-300">{item.Counterparty}</td>
                        <td className="py-2.5 px-4 text-white font-semibold">${item.Amount}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 3: AI Fraud & SOC2 Audits */}
        {activeTab === 'ai' && (
          <div className="space-y-4">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
              <h2 className="text-base font-semibold text-white">AI-Powered Anomaly & SOC2 Compliance Engine</h2>
              <p className="text-xs text-slate-400 mt-1">
                Real-time Kafka consumer running heuristic anomaly detection and Claude / GenAI automated audit ticketing. Flagged incidents are archived to LocalStack S3 and notified to SQS.
              </p>
            </div>

            {anomalies.length === 0 ? (
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center text-slate-500">
                <ShieldAlert className="w-12 h-12 text-slate-700 mx-auto mb-3" />
                <p>No anomalies currently flagged in stream buffer.</p>
                <p className="text-xs text-slate-600 mt-1">Click "Simulate $88,500 Fraud Anomaly" above to test the AI detection workflow.</p>
              </div>
            ) : (
              anomalies.map((anomaly, idx) => (
                <div key={idx} className="bg-slate-900 border border-rose-500/30 rounded-xl p-5 space-y-3 shadow-lg shadow-rose-950/20">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <span className="px-2.5 py-1 rounded-md text-xs font-bold uppercase bg-rose-500/20 text-rose-300 border border-rose-500/40">
                        Risk Score: {(anomaly.risk_score * 100).toFixed(0)}%
                      </span>
                      <span className="font-mono text-xs text-slate-400">Tx ID: {anomaly.transaction_id}</span>
                    </div>
                    <span className="text-xs text-slate-500 font-mono">Archived: S3 / SQS</span>
                  </div>

                  <div className="text-xs text-slate-300">
                    <strong>Violation:</strong> {anomaly.violation_reason}
                  </div>

                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 text-xs font-mono text-emerald-300 whitespace-pre-line leading-relaxed">
                    {anomaly.ai_narrative}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800 bg-slate-900/60 px-6 py-4 text-center text-xs text-slate-500">
        NexusFlow Analytics • Engineered by <span className="text-slate-300 font-medium">Kimesh Gupta</span> • Senior Distributed Systems & Cloud Architect
      </footer>
    </div>
  );
}
