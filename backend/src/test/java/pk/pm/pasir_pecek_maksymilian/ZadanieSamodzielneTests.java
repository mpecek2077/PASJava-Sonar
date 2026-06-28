package pk.pm.pasir_pecek_maksymilian;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import pk.pm.pasir_pecek_maksymilian.dto.*;
import pk.pm.pasir_pecek_maksymilian.model.*;
import pk.pm.pasir_pecek_maksymilian.repository.*;
import pk.pm.pasir_pecek_maksymilian.service.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // Cofa bazę danych po każdym teście
public class ZadanieSamodzielneTests {

    @Autowired GroupService groupService;
    @Autowired MembershipService membershipService;
    @Autowired DebtService debtService;
    @Autowired GroupTransactionService groupTransactionService;
    @Autowired UserRepository userRepository;
    @Autowired DebtRepository debtRepository;
    @Autowired GroupRepository groupRepository;

    User owner, member1, member2, outsider;
    Group group;

    private User createTestUser(String email, String username) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword("pass");
        return userRepository.save(user);
    }

    @BeforeEach
    void setUp() {
        // Czyszczenie kontekstu bezpieczeństwa i tworzenie użytkowników testowych
        SecurityContextHolder.clearContext();

        owner = createTestUser("owner@test.com", "Owner");
        member1 = createTestUser("member1@test.com", "Member1");
        member2 = createTestUser("member2@test.com", "Member2");
        outsider = createTestUser("outsider@test.com", "Outsider");

        // Utworzenie grupy przez właściciela
        mockLogin(owner);
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group");
        group = groupService.createGroup(groupDTO);
    }

    private void mockLogin(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
    }

    // 1. Utworzenie grupy dodaje właściciela jako członka i zwraca ją w myGroups
    @Test
    void createGroupAddsOwnerAsMember() {
        mockLogin(owner);
        List<Group> myGroups = groupService.getAllGroups();
        assertTrue(myGroups.stream().anyMatch(g -> g.getId().equals(group.getId())));
        List<Membership> members = membershipService.getGroupMembers(group.getId());
        assertEquals(1, members.size());
        assertEquals(owner.getId(), members.get(0).getUser().getId());
    }

    // 2. Tylko właściciel grupy może dodawać członków
    @Test
    void onlyGroupOwnerCanAddMembers() {
        MembershipDTO dto = new MembershipDTO();
        dto.setGroupId(group.getId());
        dto.setUserEmail(member1.getEmail());

        mockLogin(outsider); // Próba dodania przez obcego
        assertThrows(AccessDeniedException.class, () -> membershipService.addMember(dto));

        mockLogin(owner); // Próba dodania przez właściciela (Sukces)
        assertDoesNotThrow(() -> membershipService.addMember(dto));
    }

    // 3. groupMembers zwraca członków grupy tylko członkowi tej grupy
    @Test
    void groupMembersOnlyForGroupMembers() {
        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> membershipService.getGroupMembers(group.getId()));
    }

    // 4. groupDebts zwraca długi grupy tylko członkowi tej grupy
    @Test
    void groupDebtsOnlyForGroupMembers() {
        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> debtService.getGroupDebts(group.getId()));
    }

    // 5. Nowy członek dostaje tylko długi z transakcji dodanych po dołączeniu
    @Test
    void newMemberDoesNotGetHistoricalDebts() {
        // Właściciel dodaje member1
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        // Tworzą transakcję przed dołączeniem member2
        GroupTransactionDTO tDto = new GroupTransactionDTO();
        tDto.setGroupId(group.getId()); tDto.setAmount(100.0); tDto.setTitle("Pizza"); tDto.setType("EXPENSE");
        groupTransactionService.addGroupTransaction(tDto, owner);

        // Dołącza member2
        MembershipDTO mDto2 = new MembershipDTO(); mDto2.setGroupId(group.getId()); mDto2.setUserEmail(member2.getEmail());
        membershipService.addMember(mDto2);

        // Member2 sprawdza długi - nie powinien mieć żadnych przypisanych do siebie z przeszłości
        mockLogin(member2);
        List<Debt> debts = debtService.getGroupDebts(group.getId());
        boolean hasMyDebts = debts.stream().anyMatch(d -> d.getDebtor().getId().equals(member2.getId()) || d.getCreditor().getId().equals(member2.getId()));
        assertFalse(hasMyDebts);
    }

    // 6. Transakcja grupowa typu INCOME tworzy długi od aktualnego użytkownika do pozostałych
    @Test
    void incomeTransactionCreatesDebtsFromCurrentUserToOthers() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        GroupTransactionDTO tDto = new GroupTransactionDTO();
        tDto.setGroupId(group.getId()); tDto.setAmount(100.0); tDto.setTitle("Wypłata"); tDto.setType("INCOME");
        groupTransactionService.addGroupTransaction(tDto, owner);

        List<Debt> debts = debtService.getGroupDebts(group.getId());
        // Zgodnie z logiką, jeśli to INCOME, to dłużnikiem jest ten kto dodaje (owner), a wierzycielem reszta
        Debt debt = debts.get(0);
        assertEquals(owner.getId(), debt.getDebtor().getId());
        assertEquals(member1.getId(), debt.getCreditor().getId());
    }

    // 8. Nie można usunąć właściciela z jego grupy przez removeMember
    @Test
    void cannotRemoveOwnerFromGroup() {
        mockLogin(owner);
        List<Membership> members = membershipService.getGroupMembers(group.getId());
        Long ownerMembershipId = members.get(0).getId();
        assertThrows(IllegalStateException.class, () -> membershipService.removeMember(ownerMembershipId));
    }

    // 9. Członek grupy niebędący właścicielem nie może usunąć grupy
    @Test
    void nonOwnerCannotDeleteGroup() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        mockLogin(member1);
        assertThrows(AccessDeniedException.class, () -> groupService.deleteGroup(group.getId()));
    }

    // 10, 11, 13. Testowanie uprawnień ręcznego długu (createDebt)
    @Test
    void manualDebtValidations() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        DebtDTO debtDTO = new DebtDTO();
        debtDTO.setGroupId(group.getId());
        debtDTO.setAmount(50.0);
        debtDTO.setTitle("Test");

        // Dług do samego siebie (odrzucony)
        debtDTO.setDebtorId(owner.getId());
        debtDTO.setCreditorId(owner.getId());
        assertThrows(IllegalStateException.class, () -> debtService.createDebt(debtDTO));

        // Dług z osobą spoza grupy (odrzucony)
        debtDTO.setDebtorId(owner.getId());
        debtDTO.setCreditorId(outsider.getId());
        assertThrows(AccessDeniedException.class, () -> debtService.createDebt(debtDTO));

        // Członek tworzy dług, w którym NIE uczestniczy (odrzucony)
        MembershipDTO mDto2 = new MembershipDTO(); mDto2.setGroupId(group.getId()); mDto2.setUserEmail(member2.getEmail());
        membershipService.addMember(mDto2);
        mockLogin(member1); // member1 jest zalogowany
        debtDTO.setDebtorId(owner.getId()); // próbuje utworzyć dług między owner a member2
        debtDTO.setCreditorId(member2.getId());
        assertThrows(AccessDeniedException.class, () -> debtService.createDebt(debtDTO));

        // Właściciel tworzy dług między innymi (Sukces - wymóg 12)
        mockLogin(owner);
        assertDoesNotThrow(() -> debtService.createDebt(debtDTO));
    }

    // 14, 15, 16. Testowanie usuwania długu (deleteDebt)
    @Test
    void deleteDebtPermissions() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);
        MembershipDTO mDto2 = new MembershipDTO(); mDto2.setGroupId(group.getId()); mDto2.setUserEmail(member2.getEmail());
        membershipService.addMember(mDto2);

        // Właściciel tworzy dług między member1 a member2
        DebtDTO debtDTO = new DebtDTO(); debtDTO.setGroupId(group.getId()); debtDTO.setAmount(50.0);
        debtDTO.setTitle("Test"); debtDTO.setDebtorId(member1.getId()); debtDTO.setCreditorId(member2.getId());
        Debt savedDebt = debtService.createDebt(debtDTO);

        // Obcy lub nieuczestniczący nie może usunąć (wymóg 15)
        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> debtService.deleteDebt(savedDebt.getId()));

        // Właściciel, mimo że nie jest uczestnikiem, może usunąć (wymóg 16)
        mockLogin(owner);
        assertDoesNotThrow(() -> debtService.deleteDebt(savedDebt.getId()));
    }

    // 18. Usunięcie grupy przez właściciela usuwa powiązane długi i grupę
    @Test
    void deleteGroupRemovesDebtsAndGroup() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        DebtDTO debtDTO = new DebtDTO(); debtDTO.setGroupId(group.getId()); debtDTO.setAmount(50.0);
        debtDTO.setTitle("Test"); debtDTO.setDebtorId(owner.getId()); debtDTO.setCreditorId(member1.getId());
        Debt savedDebt = debtService.createDebt(debtDTO);

        // Usuwanie grupy
        groupService.deleteGroup(group.getId());

        // Sprawdzenie, czy grupa usunięta (po ID, żeby ominąć cache Hibernate)
        assertTrue(groupRepository.findById(group.getId()).isEmpty(), "Grupa powinna zostać usunięta z bazy");

        // Sprawdzenie, czy dług usunięty
        assertTrue(debtRepository.findById(savedDebt.getId()).isEmpty(), "Długi powiązane z grupą powinny zostać usunięte");
    }
}