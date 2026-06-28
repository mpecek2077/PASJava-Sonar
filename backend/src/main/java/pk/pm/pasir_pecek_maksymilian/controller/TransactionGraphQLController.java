package pk.pm.pasir_pecek_maksymilian.controller;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pk.pm.pasir_pecek_maksymilian.model.Transaction;
import pk.pm.pasir_pecek_maksymilian.service.TransactionService;

import java.util.List;

@Controller
public class TransactionGraphQLController {

    private final TransactionService transactionService;

    public TransactionGraphQLController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }
    @QueryMapping
    public List<Transaction> transactions() {
        return transactionService.getAllTransactions();
    }

    @org.springframework.graphql.data.method.annotation.MutationMapping
    public Transaction addTransaction(
            @jakarta.validation.Valid @org.springframework.graphql.data.method.annotation.Argument pk.pm.pasir_pecek_maksymilian.dto.TransactionDTO transactionDTO) {
        return transactionService.createTransaction(transactionDTO);
    }

    @org.springframework.graphql.data.method.annotation.MutationMapping
    public Transaction updateTransaction(
            @org.springframework.graphql.data.method.annotation.Argument Long id,
            @jakarta.validation.Valid @org.springframework.graphql.data.method.annotation.Argument pk.pm.pasir_pecek_maksymilian.dto.TransactionDTO transactionDTO) {
        return transactionService.updateTransaction(id, transactionDTO);
    }

    // Zadanie samodzielne: dodanie metody usuwającej
    @org.springframework.graphql.data.method.annotation.MutationMapping
    public Boolean deleteTransaction(@org.springframework.graphql.data.method.annotation.Argument Long id) {
        transactionService.deleteTransaction(id);
        return true;
    }

    @org.springframework.graphql.data.method.annotation.QueryMapping
    public pk.pm.pasir_pecek_maksymilian.dto.BalanceDTO userBalance(
            @org.springframework.graphql.data.method.annotation.Argument Double days) {

        pk.pm.pasir_pecek_maksymilian.model.User user = transactionService.getCurrentUser();

        return transactionService.getUserBalance(user, days);
    }
}