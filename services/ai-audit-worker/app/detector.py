"""
NexusFlow AI Anomaly & Fraud Detection Engine
Evaluates real-time financial transaction streams for suspicious patterns.
"""
from typing import Dict, Any, Tuple
import logging

logger = logging.getLogger("nexusflow.detector")

class AnomalyDetector:
    def __init__(self, threshold_amount: float = 10000.0):
        self.threshold_amount = threshold_amount

    def analyze_transaction(self, tx: Dict[str, Any]) -> Tuple[bool, float, str]:
        """
        Analyzes a transaction payload.
        Returns:
            - is_anomaly (bool)
            - risk_score (float between 0.0 and 1.0)
            - reason (str)
        """
        try:
            amount = float(tx.get("amount", 0.0))
        except (ValueError, TypeError):
            amount = 0.0

        sender = str(tx.get("senderAccountId", ""))
        recipient = str(tx.get("recipientAccountId", ""))
        tx_type = str(tx.get("transactionType", "PAYMENT"))

        reasons = []
        risk_score = 0.05  # Baseline benign score

        # 1. High-Value Velocity / Whale Outlier Check
        if amount >= self.threshold_amount * 5:
            risk_score += 0.70
            reasons.append(f"Extreme amount anomaly: ${amount:,.2f} exceeds 5x threshold (${self.threshold_amount:,.2f})")
        elif amount >= self.threshold_amount:
            risk_score += 0.45
            reasons.append(f"High-value threshold breached: ${amount:,.2f}")

        # 2. Self-dealing / Circular Flow Check
        if sender and recipient and sender == recipient:
            risk_score += 0.60
            reasons.append("Circular transfer detected: Sender and recipient are identical")

        # 3. Micro-probing or Structuring heuristic
        if 9900 <= amount <= 9999:
            risk_score += 0.35
            reasons.append("Potential currency structuring/smurfing pattern (amount just below $10,000 threshold)")

        is_anomaly = risk_score >= 0.50
        reason_str = "; ".join(reasons) if reasons else "Normal transaction behavior"

        return is_anomaly, min(round(risk_score, 2), 1.0), reason_str
