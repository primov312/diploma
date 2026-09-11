import json
from typing import Dict, Any
import boto3

def fetch_features_from_claim(ref, cfg) -> Dict[str, Any]:
    """
    Download the JSON payload referenced by ClaimRef from S3/MinIO.
    """
    session = boto3.session.Session()
    s3 = session.client(
        "s3",
        endpoint_url=cfg.s3_endpoint,
        aws_access_key_id=cfg.s3_access_key,
        aws_secret_access_key=cfg.s3_secret_key,
        region_name=cfg.s3_region or "us-east-1",
    )
    obj = s3.get_object(Bucket=ref.bucket, Key=ref.key)
    data = obj["Body"].read()
    return json.loads(data)