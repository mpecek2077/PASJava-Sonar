package pk.pm.pasir_pecek_maksymilian.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pk.pm.pasir_pecek_maksymilian.config.OpenApiConfig;
import pk.pm.pasir_pecek_maksymilian.dto.TransactionDTO;
import pk.pm.pasir_pecek_maksymilian.model.Transaction;
import pk.pm.pasir_pecek_maksymilian.service.TransactionService;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Personal income and expense transaction CRUD endpoints")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "List all transactions for the authenticated user")
    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @Operation(summary = "Get a transaction by ID")
    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @Operation(summary = "Create a new transaction")
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.createTransaction(transactionDTO));
    }

    @Operation(summary = "Update an existing transaction")
    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, transactionDTO));
    }

    @Operation(summary = "Delete a transaction by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.ok().build();
    }
}