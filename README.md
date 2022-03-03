## Akka Cassandra Demo

### Available Routes

#### Adding a new bank account

```shell
curl -v -X POST http://localhost:8080/bank-accounts\
   -H 'Content-Type: application/json'\
   -d '{"user":"rcardin", "currency":"EUR", "balance": 1000.0}'K
```