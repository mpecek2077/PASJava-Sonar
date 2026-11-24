package pk.pm.pasir_pecek_maksymilian;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pk.pm.pasir_pecek_maksymilian.dto.*;
import pk.pm.pasir_pecek_maksymilian.model.*;
import pk.pm.pasir_pecek_maksymilian.repository.*;
import pk.pm.pasir_pecek_maksymilian.service.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupDebtAuthorizationTests {

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
        SecurityContextHolder.clearContext();

        owner = createTestUser("owner@test.com", "Owner");
        member1 = createTestUser("member1@test.com", "Member1");
        member2 = createTestUser("member2@test.com", "Member2");
        outsider = createTestUser("outsider@test.com", "Outsider");

        mockLogin(owner);
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group");
        group = groupService.createGroup(groupDTO);
    }

    private void mockLogin(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
    }

    @Test
    void createGroupAddsOwnerAsMember() {
        mockLogin(owner);
        List<Group> myGroups = groupService.getAllGroups();
        assertTrue(myGroups.stream().anyMatch(g -> g.getId().equals(group.getId())));
        List<Membership> members = membershipService.getGroupMembers(group.getId());
        assertEquals(1, members.size());
        assertEquals(owner.getId(), members.get(0).getUser().getId());
    }

    @Test
    void onlyGroupOwnerCanAddMembers() {
        MembershipDTO dto = new MembershipDTO();
        dto.setGroupId(group.getId());
        dto.setUserEmail(member1.getEmail());

        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> membershipService.addMember(dto));

        mockLogin(owner);
        assertDoesNotThrow(() -> membershipService.addMember(dto));
    }

    @Test
    void groupMembersOnlyForGroupMembers() {
        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> membershipService.getGroupMembers(group.getId()));
    }

    @Test
    void groupDebtsOnlyForGroupMembers() {
        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> debtService.getGroupDebts(group.getId()));
    }

    @Test
    void newMemberDoesNotGetHistoricalDebts() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        GroupTransactionDTO tDto = new GroupTransactionDTO();
        tDto.setGroupId(group.getId()); tDto.setAmount(100.0); tDto.setTitle("Pizza"); tDto.setType("EXPENSE");
        groupTransactionService.addGroupTransaction(tDto, owner);

        MembershipDTO mDto2 = new MembershipDTO(); mDto2.setGroupId(group.getId()); mDto2.setUserEmail(member2.getEmail());
        membershipService.addMember(mDto2);

        mockLogin(member2);
        List<Debt> debts = debtService.getGroupDebts(group.getId());
        boolean hasMyDebts = debts.stream().anyMatch(d -> d.getDebtor().getId().equals(member2.getId()) || d.getCreditor().getId().equals(member2.getId()));
        assertFalse(hasMyDebts);
    }

    @Test
    void incomeTransactionCreatesDebtsFromCurrentUserToOthers() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        GroupTransactionDTO tDto = new GroupTransactionDTO();
        tDto.setGroupId(group.getId()); tDto.setAmount(100.0); tDto.setTitle("Wypłata"); tDto.setType("INCOME");
        groupTransactionService.addGroupTransaction(tDto, owner);

        List<Debt> debts = debtService.getGroupDebts(group.getId());
        Debt debt = debts.get(0);
        assertEquals(owner.getId(), debt.getDebtor().getId());
        assertEquals(member1.getId(), debt.getCreditor().getId());
    }

    @Test
    void cannotRemoveOwnerFromGroup() {
        mockLogin(owner);
        List<Membership> members = membershipService.getGroupMembers(group.getId());
        Long ownerMembershipId = members.get(0).getId();
        assertThrows(IllegalStateException.class, () -> membershipService.removeMember(ownerMembershipId));
    }

    @Test
    void nonOwnerCannotDeleteGroup() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        mockLogin(member1);
        assertThrows(AccessDeniedException.class, () -> groupService.deleteGroup(group.getId()));
    }

    @Test
    void manualDebtValidations() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        DebtDTO debtDTO = new DebtDTO();
        debtDTO.setGroupId(group.getId());
        debtDTO.setAmount(50.0);
        debtDTO.setTitle("Test");

        debtDTO.setDebtorId(owner.getId());
        debtDTO.setCreditorId(owner.getId());
        assertThrows(IllegalStateException.class, () -> debtService.createDebt(debtDTO));

        debtDTO.setDebtorId(owner.getId());
        debtDTO.setCreditorId(outsider.getId());
        assertThrows(AccessDeniedException.class, () -> debtService.createDebt(debtDTO));

        MembershipDTO mDto2 = new MembershipDTO(); mDto2.setGroupId(group.getId()); mDto2.setUserEmail(member2.getEmail());
        membershipService.addMember(mDto2);
        mockLogin(member1);
        debtDTO.setDebtorId(owner.getId());
        debtDTO.setCreditorId(member2.getId());
        assertThrows(AccessDeniedException.class, () -> debtService.createDebt(debtDTO));

        mockLogin(owner);
        assertDoesNotThrow(() -> debtService.createDebt(debtDTO));
    }

    @Test
    void deleteDebtPermissions() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);
        MembershipDTO mDto2 = new MembershipDTO(); mDto2.setGroupId(group.getId()); mDto2.setUserEmail(member2.getEmail());
        membershipService.addMember(mDto2);

        DebtDTO debtDTO = new DebtDTO(); debtDTO.setGroupId(group.getId()); debtDTO.setAmount(50.0);
        debtDTO.setTitle("Test"); debtDTO.setDebtorId(member1.getId()); debtDTO.setCreditorId(member2.getId());
        Debt savedDebt = debtService.createDebt(debtDTO);

        mockLogin(outsider);
        assertThrows(AccessDeniedException.class, () -> debtService.deleteDebt(savedDebt.getId()));

        mockLogin(owner);
        assertDoesNotThrow(() -> debtService.deleteDebt(savedDebt.getId()));
    }

    @Test
    void deleteGroupRemovesDebtsAndGroup() {
        mockLogin(owner);
        MembershipDTO mDto = new MembershipDTO(); mDto.setGroupId(group.getId()); mDto.setUserEmail(member1.getEmail());
        membershipService.addMember(mDto);

        DebtDTO debtDTO = new DebtDTO(); debtDTO.setGroupId(group.getId()); debtDTO.setAmount(50.0);
        debtDTO.setTitle("Test"); debtDTO.setDebtorId(owner.getId()); debtDTO.setCreditorId(member1.getId());
        Debt savedDebt = debtService.createDebt(debtDTO);

        groupService.deleteGroup(group.getId());

        assertTrue(groupRepository.findById(group.getId()).isEmpty(), "Expected group to be deleted");
        assertTrue(debtRepository.findById(savedDebt.getId()).isEmpty(), "Expected group debts to be deleted");
    }
}
