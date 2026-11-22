package pe.edu.upc.dayudita.clients.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.upc.dayudita.clients.domain.model.Client;
import pe.edu.upc.dayudita.clients.domain.model.ClientAccount;
import pe.edu.upc.dayudita.clients.domain.repository.ClientAccountRepository;
import pe.edu.upc.dayudita.clients.domain.repository.ClientRepository;
import pe.edu.upc.dayudita.clients.interfaces.rest.dto.CreateClientRequest;
import pe.edu.upc.dayudita.clients.interfaces.rest.dto.UpdateClientRequest;
import pe.edu.upc.dayudita.finance.application.FinancialConfigurationService;
import pe.edu.upc.dayudita.finance.domain.model.FinancialConfiguration;
import pe.edu.upc.dayudita.iam.application.CurrentUserService;
import pe.edu.upc.dayudita.iam.domain.repository.AdministratorRepository;
import pe.edu.upc.dayudita.iam.security.ApplicationUserDetails;
import pe.edu.upc.dayudita.stores.domain.model.Store;
import pe.edu.upc.dayudita.stores.domain.repository.StoreRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientServiceTest {

    private ClientRepository clientRepository;
    private ClientAccountRepository accountRepository;
    private AdministratorRepository administratorRepository;
    private ClientService service;
    private Store store;

    @BeforeEach
    void setUp(){
        store = new Store();
        store.setId(1L);
        store.setActive(true);

        FinancialConfiguration policy = new FinancialConfiguration();
        policy.setStore(store);
        policy.setCapitalMinimo(new BigDecimal("200.00"));
        policy.setCapitalMaximo(new BigDecimal("500.00"));
        policy.setTeaMinima(new BigDecimal("0.15"));
        policy.setTeaMaxima(new BigDecimal("0.25"));
        policy.setPlazoMaximoMeses(4);
        policy.setOtorgaGracia(true);

        clientRepository = mock(ClientRepository.class);
        when(clientRepository.save(any())).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            if(client.getId() == null) client.setId(10L);
            return client;
        });
        when(clientRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(clientRepository.findByDocumentNumber(anyString())).thenReturn(Optional.empty());

        accountRepository = mock(ClientAccountRepository.class);
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        administratorRepository = mock(AdministratorRepository.class);
        when(administratorRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        StoreRepository storeRepository = mock(StoreRepository.class);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        FinancialConfigurationService configurationService = mock(FinancialConfigurationService.class);
        when(configurationService.findConfiguration(1L)).thenReturn(Optional.of(policy));

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");

        service = new ClientService(
                clientRepository, accountRepository, storeRepository, administratorRepository,
                configurationService, encoder, new CurrentUserService(storeRepository), new BigDecimal("0.40")
        );

        ApplicationUserDetails admin = new ApplicationUserDetails(
                2L, "Admin", "Dayu", "admin@dayudita.pe", "x", "STORE_ADMIN", 1L, "Dayu Centro", true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities())
        );
    }

    @AfterEach
    void clearSecurity(){
        SecurityContextHolder.clearContext();
    }

    @Test
    void createClient_requiresOnlyPersonalData(){
        ClientAccount account = service.createClient(1L, new CreateClientRequest(
                "Lucía", "Ramos", "70000099", "lucia@correo.pe", "secreto1", "999000111"
        ));

        assertEquals("Lucía", account.getClient().getFirstName());
        assertEquals("70000099", account.getClient().getDocumentNumber());
        assertNotNull(account.getTeaPactada());
        assertTrue(account.getTeaPactada().compareTo(new BigDecimal("0.15")) >= 0
                && account.getTeaPactada().compareTo(new BigDecimal("0.25")) <= 0);
        assertEquals(new BigDecimal("0.40"), account.getTeaMoratoriaPactada());
        assertEquals(new BigDecimal("500.00"), account.getLimiteCredito());
        assertEquals(4, account.getPlazoMaximoMeses());
        assertNotNull(account.getDiaCorte());
        assertNotNull(account.getCurrency());
    }

    @Test
    void updateClient_editsPersonalDataWithoutTouchingInternalAccountFields(){
        Client client = new Client();
        client.setId(10L);
        client.setFirstName("Ana");
        client.setEmail("ana@correo.pe");
        client.setDocumentNumber("70000001");

        ClientAccount account = new ClientAccount();
        account.setClient(client);
        account.setStore(store);
        account.setTeaPactada(new BigDecimal("0.20"));
        account.setTeaMoratoriaPactada(new BigDecimal("0.12"));
        account.setLimiteCredito(new BigDecimal("1000.00"));
        account.setPlazoMaximoMeses(4);
        account.setDiaCorte(15);
        when(accountRepository.findByClient_IdAndStore_Id(10L, 1L)).thenReturn(Optional.of(account));

        service.updateClient(1L, 10L, new UpdateClientRequest(
                "Ana María", "Torres", "70000002", "ana.torres@correo.pe", "988777666"
        ));

        assertEquals("Ana María", client.getFirstName());
        assertEquals("70000002", client.getDocumentNumber());
        assertEquals("ana.torres@correo.pe", client.getEmail());
        assertEquals(new BigDecimal("0.12"), account.getTeaMoratoriaPactada(), "no se reescriben datos historicos");
        assertEquals(new BigDecimal("1000.00"), account.getLimiteCredito());
    }

    @Test
    void updateClient_rejectsEmailOrDocumentOfAnotherClient(){
        Client client = new Client();
        client.setId(10L);
        ClientAccount account = new ClientAccount();
        account.setClient(client);
        account.setStore(store);
        when(accountRepository.findByClient_IdAndStore_Id(10L, 1L)).thenReturn(Optional.of(account));

        Client other = new Client();
        other.setId(11L);
        when(clientRepository.findByEmailIgnoreCase("ocupado@correo.pe")).thenReturn(Optional.of(other));
        when(clientRepository.findByDocumentNumber("70000003")).thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class, () -> service.updateClient(1L, 10L,
                new UpdateClientRequest("Ana", "Torres", "70000001", "ocupado@correo.pe", null)));
        assertThrows(IllegalArgumentException.class, () -> service.updateClient(1L, 10L,
                new UpdateClientRequest("Ana", "Torres", "70000003", "libre@correo.pe", null)));
    }
}
