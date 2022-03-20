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
curl -v -X PUT http://localhost:8080/bank-accounts/d2f6c2f5-0838-4fb7-a745-119be8c31c4b\
   -H 'Content-Type: application/json'\
   -d '{"currency":"EUR", "amount": 500.0}'
```

#### Retrieving the details of a bank account

```shell
curl -v http://localhost:8080/bank-accounts/ce1f4ac3-f1be-4523-b323-25e81d90322f
```