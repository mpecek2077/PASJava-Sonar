package pk.pm.pasir_pecek_maksymilian.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared HTTP-level integration test suite covering JWT auth, REST transactions,
 * GraphQL group/debt workflows, and authorization boundaries.
 */
abstract class AbstractApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected String registerAndLogin(String username, String email, String password) throws Exception {
        String registerBody = """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(username, email, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        String loginBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token");
        assertNotNull(token);
        assertFalse(token.isBlank());
        return token;
    }

    protected String graphqlPayload(String query) {
        String escaped = query
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
        return "{\"query\":\"" + escaped + "\"}";
    }

    @Test
    void authenticationAndSecurityBoundaries() throws Exception {
        String email = uniqueEmail("auth");
        registerAndLogin("AuthUser", email, "Secret123!");

        String wrongPasswordBody = """
                {
                  "email": "%s",
                  "password": "WrongPassword!"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transactionCrudAndUserIsolation() throws Exception {
        String aliceToken = registerAndLogin("Alice", uniqueEmail("alice"), "Secret123!");
        String bobToken = registerAndLogin("Bob", uniqueEmail("bob"), "Secret123!");

        String createIncome = """
                {
                  "amount": 2500.00,
                  "type": "INCOME",
                  "tags": "salary",
                  "notes": "Monthly payout"
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createIncome))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amount").value(2500.0))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andReturn();

        Number txId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Bob cannot view, modify, or delete Alice's transaction
        mockMvc.perform(get("/api/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Alice updates and verifies her transaction
        String updateBody = """
                {
                  "amount": 2750.50,
                  "type": "INCOME",
                  "tags": "salary,bonus",
                  "notes": "Updated payout"
                }
                """;

        mockMvc.perform(put("/api/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(2750.50))
                .andExpect(jsonPath("$.notes").value("Updated payout"));

        // Invalid payload is rejected by Bean Validation
        String invalidBody = """
                {
                  "amount": -10.0,
                  "type": "INVALID",
                  "tags": "bad",
                  "notes": "nope"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        // Alice deletes her transaction and subsequent lookup returns 404
        mockMvc.perform(delete("/api/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void graphQlBalanceGroupSplitAndDebtSettlementFlow() throws Exception {
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        String outsiderEmail = uniqueEmail("outsider");

        String ownerToken = registerAndLogin("Owner", ownerEmail, "Secret123!");
        String memberToken = registerAndLogin("Member", memberEmail, "Secret123!");
        String outsiderToken = registerAndLogin("Outsider", outsiderEmail, "Secret123!");

        // Create personal transactions and verify GraphQL userBalance
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.0,\"type\":\"INCOME\",\"tags\":\"work\",\"notes\":\"Salary\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":350.0,\"type\":\"EXPENSE\",\"tags\":\"rent\",\"notes\":\"Bills\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload("query { userBalance { totalIncome totalExpense balance } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userBalance.totalIncome").value(closeTo(1000.0, 0.01)))
                .andExpect(jsonPath("$.data.userBalance.totalExpense").value(closeTo(350.0, 0.01)))
                .andExpect(jsonPath("$.data.userBalance.balance").value(closeTo(650.0, 0.01)));

        // Create group and add member via GraphQL
        MvcResult createGroupRes = mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload("mutation { createGroup(groupDTO: { name: \"Trip\" }) { id name } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createGroup.name").value("Trip"))
                .andReturn();

        String groupId = JsonPath.read(createGroupRes.getResponse().getContentAsString(), "$.data.createGroup.id").toString();

        mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload(
                                "mutation { addMember(membershipDTO: { groupId: " + groupId + ", userEmail: \"" + memberEmail + "\" }) { id userEmail } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addMember.userEmail").value(memberEmail));

        // Split a 120.00 group expense -> member owes owner 60.00
        mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload(
                                "mutation { addGroupTransaction(groupTransactionDTO: { groupId: " + groupId + ", amount: 120.0, type: \"EXPENSE\", title: \"Dinner\" }) }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addGroupTransaction").value(true));

        MvcResult debtsRes = mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload(
                                "query { groupDebts(groupId: " + groupId + ") { id amount title paidByDebtor confirmedByCreditor debtor { email } creditor { email } } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupDebts", hasSize(1)))
                .andExpect(jsonPath("$.data.groupDebts[0].amount").value(closeTo(60.0, 0.01)))
                .andExpect(jsonPath("$.data.groupDebts[0].debtor.email").value(memberEmail))
                .andExpect(jsonPath("$.data.groupDebts[0].creditor.email").value(ownerEmail))
                .andExpect(jsonPath("$.data.groupDebts[0].paidByDebtor").value(false))
                .andExpect(jsonPath("$.data.groupDebts[0].confirmedByCreditor").value(false))
                .andReturn();

        String debtId = JsonPath.read(debtsRes.getResponse().getContentAsString(), "$.data.groupDebts[0].id").toString();

        // Outsider cannot read group debts
        mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload("query { groupDebts(groupId: " + groupId + ") { id } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", not(empty())));

        // Debtor marks debt as paid, then creditor confirms
        mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload("mutation { markDebtAsPaid(debtId: " + debtId + ") { id paidByDebtor confirmedByCreditor } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markDebtAsPaid.paidByDebtor").value(true))
                .andExpect(jsonPath("$.data.markDebtAsPaid.confirmedByCreditor").value(false));

        mockMvc.perform(post("/graphql")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(graphqlPayload("mutation { confirmDebtPayment(debtId: " + debtId + ") { id paidByDebtor confirmedByCreditor } }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmDebtPayment.paidByDebtor").value(true))
                .andExpect(jsonPath("$.data.confirmDebtPayment.confirmedByCreditor").value(true));
    }
}
