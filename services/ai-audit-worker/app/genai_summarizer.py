"""
NexusFlow GenAI Incident Summarizer
Generates SOC2-ready incident narratives and compliance audit briefs using LLMs.
"""
import os
import logging
from typing import Dict, Any

logger = logging.getLogger("nexusflow.genai")

class GenAISummarizer:
    def __init__(self):
        self.provider = os.getenv("LLM_PROVIDER", "mock").lower()
        self.api_key = os.getenv("ANTHROPIC_API_KEY", "")

    async def generate_incident_brief(self, tx: Dict[str, Any], risk_score: float, reason: str) -> str:
        """
        Generates an executive compliance explanation for the flagged anomaly.
        """
        tx_id = tx.get("id", "UNKNOWN")
        amount = tx.get("amount", 0)
        currency = tx.get("currency", "USD")
        sender = tx.get("senderAccountId", "UNKNOWN")
        recipient = tx.get("recipientAccountId", "UNKNOWN")

        # Fallback / Built-in high quality template generator (Zero-cloud dependency)
        narrative = (
            f"⚠️ [SOC2 AUDIT ALERT - {tx_id}]\n"
            f"Anomalous transaction detected with Risk Score {risk_score:.2f} / 1.00.\n"
            f"• Transfer: {sender} ➔ {recipient} for {amount} {currency}\n"
            f"• Primary Violation: {reason}\n"
            f"• Recommended Action: Temporary hold placed on outbound settlement; "
            f"compliance officer review required within 24 hours under FinCEN BSA guidelines."
        )

        # Optional integration with Anthropic Claude API if key is present
        if self.provider == "claude" and self.api_key:
            try:
                import httpx
                prompt = (
                    f"You are an enterprise financial fraud & SOC2 compliance AI. "
                    f"Summarize this transaction anomaly succinctly into an actionable audit ticket: "
                    f"TxId: {tx_id}, Amount: {amount} {currency}, Sender: {sender}, Recipient: {recipient}, "
                    f"Risk Score: {risk_score}, Reason: {reason}"
                )
                async with httpx.AsyncClient(timeout=10.0) as client:
                    resp = await client.post(
                        "https://api.anthropic.com/v1/messages",
                        headers={
                            "x-api-key": self.api_key,
                            "anthropic-version": "2023-06-01",
                            "content-type": "application/json"
                        },
                        json={
                            "model": "claude-3-haiku-20240307",
                            "max_tokens": 200,
                            "messages": [{"role": "user", "content": prompt}]
                        }
                    )
                    if resp.status_code == 200:
                        data = resp.json()
                        return data["content"][0]["text"]
            except Exception as e:
                logger.warning(f"Claude API dispatch failed, falling back to deterministic template: {e}")

        return narrative
