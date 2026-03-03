# Usage

## From OpenAPI

```bash
# To SDK
cdd-kotlin from_openapi to_sdk -i spec.json -o ./my-sdk

# To CLI SDK
cdd-kotlin from_openapi to_sdk_cli -i spec.json -o ./my-cli

# To Server
cdd-kotlin from_openapi to_server -i spec.json -o ./my-server
```

## To OpenAPI

```bash
cdd-kotlin to_openapi -f ./my-sdk -o spec.yaml --format yaml
```

## JSON RPC

```bash
cdd-kotlin serve_json_rpc --port 8080 --listen 127.0.0.1
```