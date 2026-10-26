import pytest
from app.detector import AnomalyDetector

def test_benign_transaction():
    detector = AnomalyDetector(threshold_amount=10000.0)
    tx = {
        "id": "TX_100",
        "senderAccountId": "ACCT_A",
        "recipientAccountId": "ACCT_B",
        "amount": 250.00,
        "transactionType": "PAYMENT"
    }
    is_anomaly, score, reason = detector.analyze_transaction(tx)
    assert not is_anomaly
    assert score < 0.50

def test_high_value_whale_anomaly():
    detector = AnomalyDetector(threshold_amount=10000.0)
    tx = {
        "id": "TX_999",
        "senderAccountId": "ACCT_WHALE",
        "recipientAccountId": "ACCT_CORP",
        "amount": 75000.00,
        "transactionType": "WIRE"
    }
    is_anomaly, score, reason = detector.analyze_transaction(tx)
    assert is_anomaly
    assert score >= 0.70
    assert "Extreme amount anomaly" in reason

def test_circular_self_transfer():
    detector = AnomalyDetector(threshold_amount=10000.0)
    tx = {
        "id": "TX_CIRCULAR",
        "senderAccountId": "ACCT_SAME",
        "recipientAccountId": "ACCT_SAME",
        "amount": 100.00,
        "transactionType": "PAYMENT"
    }
    is_anomaly, score, reason = detector.analyze_transaction(tx)
    assert is_anomaly
    assert "Circular transfer" in reason
