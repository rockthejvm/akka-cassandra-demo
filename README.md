## Akka Cassandra Demo

### Available Routes

#### Adding a new bank account

```shell
curl -v -X POST http://localhost:8080/bank-accounts\
   -H 'Content-Type: application/json'\
   -d '{"user":"rcardin", "currency":"EUR", "balance": 1000.0}'
```

#### Updating the balance of a bank account

```shell
curl -v -X PUT http://localhost:8080/bank-accounts/ce1f4ac3-f1be-4523-b323-25e81d90322f\
   -H 'Content-Type: application/json'\
   -d '{"currency":"EUR", "amount": 500.0}'
```