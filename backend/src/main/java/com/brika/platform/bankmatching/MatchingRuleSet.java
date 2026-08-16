package com.brika.platform.bankmatching;

import java.util.List;

/** A validated, parsed bank_criteria_versions.rules payload — always non-empty (§2, minItems 1). */
public record MatchingRuleSet(List<MatchingRule> rules) {}
