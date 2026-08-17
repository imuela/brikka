package com.brika.platform.scoring.web;

import com.brika.platform.scoring.ScoringCategoryInput;
import com.brika.platform.scoring.ScoringRuleInput;
import java.util.List;

public record CreateScoringRulesetApiRequest(
    String code,
    String version,
    List<ScoringCategoryInput> categories,
    List<ScoringRuleInput> rules) {}
