package com.brika.platform.seed;

import com.brika.platform.auth.UserCredentialService;
import com.brika.platform.bank.BankRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRepository;
import com.brika.platform.identity.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sprint 24 (seed reproducible e idempotente): crea una empresa de demostración, los usuarios
 * locales (SUPERADMIN/MANAGER/BROKER) y un catálogo mínimo de bancos. Es idempotente — consulta
 * antes de insertar, de modo que puede ejecutarse en cada arranque sin duplicar ni tocar datos ya
 * presentes — y está protegido contra producción por triple vía:
 *
 * <ol>
 *   <li>{@code @Profile({"local","test"})}: el bean ni siquiera se registra fuera de esos perfiles;
 *   <li>{@code @ConditionalOnProperty(brika.seed.enabled)}: inactivo salvo opt-in explícito;
 *   <li>{@link com.brika.platform.config.ProdEnvironmentValidator}: si por error quedara activo en
 *       PROD, el arranque se aborta (fail-closed) antes de que el runner pueda ejecutarse.
 * </ol>
 *
 * <p>Las contraseñas se fijan con el mismo {@code PasswordEncoder} (Argon2id) del sistema vía
 * {@link UserCredentialService}, y solo si el usuario aún no tiene credencial, para no pisar la que
 * un dev haya cambiado en un arranque anterior.
 */
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(name = "brika.seed.enabled", havingValue = "true")
public class DevSeedRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

  private static final String SEED_COMPANY_LEGAL_NAME = "Brika Demo S.L.";
  private static final String SEED_COMPANY_TRADE_NAME = "Brika";
  private static final String SEED_COMPANY_TAX_ID = "A00000000";

  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
  private final UserProvisioningService userProvisioningService;
  private final UserCredentialService userCredentialService;
  private final BankRepository bankRepository;
  private final String defaultPassword;

  public DevSeedRunner(
      CompanyRepository companyRepository,
      UserRepository userRepository,
      UserProvisioningService userProvisioningService,
      UserCredentialService userCredentialService,
      BankRepository bankRepository,
      @Value("${brika.seed.default-password:brika_dev_password}") String defaultPassword) {
    this.companyRepository = companyRepository;
    this.userRepository = userRepository;
    this.userProvisioningService = userProvisioningService;
    this.userCredentialService = userCredentialService;
    this.bankRepository = bankRepository;
    this.defaultPassword = defaultPassword;
  }

  @Override
  public void run(String... args) {
    UUID companyId = ensureCompany();
    ensureUser(UserRole.SUPERADMIN, null, "superadmin@brika.local", "Super", "Admin");
    ensureUser(UserRole.MANAGER, companyId, "manager@brika.local", "Demo", "Manager");
    ensureUser(UserRole.BROKER, companyId, "broker@brika.local", "Demo", "Broker");
    ensureBanks();
    log.info(
        "Dev seed aplicado (empresa={}, usuarios=superadmin/manager/broker, bancos) — idempotente y"
            + " solo local/test.",
        companyId);
  }

  private UUID ensureCompany() {
    Optional<UUID> existing =
        companyRepository.findAll().stream()
            .filter(c -> SEED_COMPANY_TAX_ID.equals(c.taxId()))
            .findFirst()
            .map(c -> c.id());
    if (existing.isPresent()) {
      return existing.get();
    }
    return companyRepository.insert(
        SEED_COMPANY_LEGAL_NAME, SEED_COMPANY_TRADE_NAME, SEED_COMPANY_TAX_ID);
  }

  private void ensureUser(
      UserRole role, UUID companyId, String email, String firstName, String lastName) {
    List<User> matches = userRepository.findAllByEmail(email);
    if (!matches.isEmpty()) {
      User user = matches.get(0);
      if (!userCredentialService.hasCredential(user.id())) {
        userCredentialService.setPassword(user.id(), defaultPassword);
        log.debug("Seed: credencial inicial para {}", email);
      }
      return;
    }
    User created =
        userProvisioningService.createUser(
            new CreateUserCommand(role, companyId, "seed-" + email, email, firstName, lastName));
    userCredentialService.setPassword(created.id(), defaultPassword);
    log.debug("Seed: creado usuario {}", email);
  }

  private void ensureBanks() {
    List<String> existingCodes = bankRepository.findAll().stream().map(b -> b.code()).toList();
    for (BankSeed bank : SEED_BANKS) {
      if (existingCodes.contains(bank.code())) {
        continue;
      }
      bankRepository.insert(bank.code(), bank.name(), bank.metadataJson());
    }
  }

  private record BankSeed(String code, String name, String metadataJson) {
    public String metadataJson() {
      return metadataJson == null ? "{}" : metadataJson;
    }
  }

  private static final List<BankSeed> SEED_BANKS =
      List.of(
          new BankSeed("BANCO_SANTANDER", "Banco Santander", null),
          new BankSeed("BANCO_BBVA", "Banco Bilbao Vizcaya Argentaria", null),
          new BankSeed("BANCO_CAIXABANK", "CaixaBank", null));
}
