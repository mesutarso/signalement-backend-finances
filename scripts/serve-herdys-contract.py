"""Launch the unmodified Herdys app with synthetic provider responses for contract tests.

Usage: python scripts/serve-herdys-contract.py /path/to/ocr-flask-coolify/app.py
Requires the dependencies from the delivered OCR requirements.txt. No real keys or
provider calls are used, and the delivered application's .env is not loaded.
"""

import argparse
import importlib.util
import json
import os
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", type=Path)
    parser.add_argument("--port", type=int, default=15001)
    args = parser.parse_args()
    fixture = json.loads((Path(__file__).resolve().parents[1] / "src/test/resources/ocr/herdys-response.json").read_text())
    settings = {
        "API_TOKEN": "contract-token",
        "REQUIRE_API_TOKEN": "true",
        "MISTRAL_API_KEY": "contract-mistral",
        "OPENAI_API_KEY": "contract-openai",
    }

    def provider_response(url, *, json, headers, timeout):
        assert timeout == 90
        assert headers["Authorization"] in ("Bearer contract-mistral", "Bearer contract-openai")
        if url == "https://api.mistral.ai/v1/ocr":
            assert json["document"]["image_url"].startswith("data:image/png;base64,")
            result = {"pages": [{"markdown": "Facture de test synthétique"}]}
        elif url in ("https://api.mistral.ai/v1/chat/completions", "https://api.openai.com/v1/chat/completions"):
            result = {"choices": [{"message": {"content": __import__("json").dumps(fixture)}}]}
        else:
            raise AssertionError("Unexpected provider URL")
        return SimpleNamespace(ok=True, status_code=200, json=lambda: result)

    with patch.dict(os.environ, settings), patch("dotenv.load_dotenv", return_value=False), patch("requests.post", side_effect=provider_response):
        spec = importlib.util.spec_from_file_location("herdys_contract_app", args.app.resolve())
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.app.run(host="127.0.0.1", port=args.port, debug=False, use_reloader=False)


if __name__ == "__main__":
    main()
