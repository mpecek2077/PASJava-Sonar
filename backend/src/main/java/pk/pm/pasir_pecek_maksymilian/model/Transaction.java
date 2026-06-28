package pk.pm.pasir_pecek_maksymilian.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double amount; // Transaction amount

    @Enumerated(EnumType.STRING)
    private TransactionType type; // Transaction type (INCOME or EXPENSE)

    private String tags; // List of tags or a single tag (as a String for simplicity)

    private String notes; // Additional notes

    private LocalDateTime timestamp; // Date and time the transaction was created

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}