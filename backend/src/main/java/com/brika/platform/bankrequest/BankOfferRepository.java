package com.brika.platform.bankrequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * bank_offers.status: unlike bank_requests/bank_responses, FUNCTIONAL_SPECIFICATION.md §17 lists
 * "estado" among the fields an offer "podrá registrar" alongside amount/term/conditions — but no
 * catalog of values is given anywhere. Sprint 6A applies the same conservative-default discipline
 * as the other tables rather than inventing a semantic enum: defaulted once at creation to
 * 'RECEIVED', server-controlled. Decision D3 explicitly forbids introducing a "rejected by
 * selection" state, so no endpoint in this sprint changes it. bank_id is always derived from the
 * parent bank_request (never accepted from the client) — an offer must be from the bank the request
 * was sent to.
 */
@Repository
public class BankOfferRepository {

  private static final String SELECT =
      "SELECT id, company_id, bank_request_id, bank_id, status, amount, interest_rate,"
          + " term_months, payment, conditions, received_at, created_at, updated_at FROM"
          + " bank_offers";

  private static final RowMapper<BankOffer> ROW_MAPPER =
      (rs, rowNum) ->
          new BankOffer(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("bank_request_id"),
              (UUID) rs.getObject("bank_id"),
              rs.getString("status"),
              rs.getBigDecimal("amount"),
              rs.getBigDecimal("interest_rate"),
              rs.getInt("term_months"),
              rs.getBigDecimal("payment"),
              rs.getString("conditions"),
              rs.getTimestamp("received_at").toInstant(),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankOfferRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID bankRequestId,
      UUID bankId,
      BigDecimal amount,
      BigDecimal interestRate,
      int termMonths,
      BigDecimal payment,
      String conditionsJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_offers (company_id, bank_request_id, bank_id, status, amount,"
            + " interest_rate, term_months, payment, conditions, received_at) VALUES (?, ?, ?,"
            + " 'RECEIVED', ?, ?, ?, ?, ?::jsonb, now()) RETURNING id",
        UUID.class,
        companyId,
        bankRequestId,
        bankId,
        amount,
        interestRate,
        termMonths,
        payment,
        conditionsJson);
  }

  public Optional<BankOffer> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<BankOffer> findAllByBankRequestId(UUID bankRequestId) {
    return jdbcTemplate.query(
        SELECT + " WHERE bank_request_id = ? ORDER BY created_at DESC", ROW_MAPPER, bankRequestId);
  }

  public List<BankOffer> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT
            + " WHERE bank_request_id IN (SELECT id FROM bank_requests WHERE case_id = ?) ORDER"
            + " BY created_at DESC",
        ROW_MAPPER,
        caseId);
  }
}
