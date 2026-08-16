package com.brika.platform.activity;

import org.springframework.stereotype.Component;

@Component
public class SynchronousActivityPublisher implements ActivityPublisher {

  private final ActivityRepository activityRepository;

  public SynchronousActivityPublisher(ActivityRepository activityRepository) {
    this.activityRepository = activityRepository;
  }

  @Override
  public void publish(CaseActivityEvent event) {
    if (event.actorClientId() != null) {
      activityRepository.insertWithClientActor(
          event.companyId(),
          event.caseId(),
          event.actorClientId(),
          event.activityType(),
          event.summary());
    } else {
      activityRepository.insert(
          event.companyId(),
          event.caseId(),
          event.actorUserId(),
          event.activityType(),
          event.summary());
    }
  }
}
