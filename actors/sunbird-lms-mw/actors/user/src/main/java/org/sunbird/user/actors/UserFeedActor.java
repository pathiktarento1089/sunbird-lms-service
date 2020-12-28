package org.sunbird.user.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.feed.IFeedService;
import org.sunbird.feed.impl.FeedFactory;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.Feed;
import org.sunbird.models.user.FeedStatus;

/** This class contains API related to user feed. */
@ActorConfig(
  tasks = {"getUserFeedById", "createUserFeed", "updateUserFeed", "deleteUserFeed"},
  asyncTasks = {},
  dispatcher = "most-used-two-dispatcher"
)
public class UserFeedActor extends BaseActor {

  IFeedService feedService = FeedFactory.getInstance();
  ObjectMapper mapper = new ObjectMapper();

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    RequestContext context = request.getRequestContext();
    String operation = request.getOperation();
    switch (operation) {
      case "getUserFeedById":
        logger.info(context, "UserFeedActor:onReceive getUserFeed method called");
        String userId = (String) request.getRequest().get(JsonKey.USER_ID);
        getUserFeed(userId, context);
        break;
      case "createUserFeed":
        logger.info(context, "UserFeedActor:onReceive createUserFeed method called");
        createUserFeed(request, context);
        break;
      case "deleteUserFeed":
        logger.info(context, "UserFeedActor:onReceive deleteUserFeed method called");
        deleteUserFeed(request, context);
        break;
      case "updateUserFeed":
        logger.info(context, "UserFeedActor:onReceive updateUserFeed method called");
        updateUserFeed(request, context);
        break;
      default:
        onReceiveUnsupportedOperation("UserFeedActor");
    }
  }

  private void getUserFeed(String userId, RequestContext context) {
    Map<String, Object> reqMap = new WeakHashMap<>(2);
    reqMap.put(JsonKey.USER_ID, userId);
    List<Feed> feedList = feedService.getFeedsByProperties(reqMap, context);
    Map<String, Object> result = new HashMap<>();
    Response response = new Response();
    response.put(JsonKey.RESPONSE, result);
    result.put(JsonKey.USER_FEED, feedList);
    sender().tell(response, self());
  }

  private void createUserFeed(Request request, RequestContext context) {
    Feed feed = mapper.convertValue(request.getRequest(), Feed.class);
    feed.setStatus(FeedStatus.UNREAD.getfeedStatus());
    Response feedCreateResponse = feedService.insert(feed, context);
    sender().tell(feedCreateResponse, self());
    // Delete the old user feed
    Map<String, Object> reqMap = new WeakHashMap<>(2);
    reqMap.put(JsonKey.USER_ID, feed.getUserId());
    List<Feed> feedList = feedService.getFeedsByProperties(reqMap, context);
    if (feedList.size() >= Integer.parseInt(ProjectUtil.getConfigValue(JsonKey.FEED_LIMIT))) {
      feedList.sort(Comparator.comparing(Feed::getCreatedOn));
      Feed delRecord = feedList.get(0);
      feedService.delete(
          delRecord.getId(), delRecord.getUserId(), delRecord.getCategory(), context);
    }
  }

  private void deleteUserFeed(Request request, RequestContext context) {
    Response feedDeleteResponse = new Response();
    Map<String, Object> deleteRequest = request.getRequest();
    feedService.delete(
        (String) deleteRequest.get(JsonKey.FEED_ID),
        (String) deleteRequest.get(JsonKey.USER_ID),
        (String) deleteRequest.get(JsonKey.CATEGORY),
        context);
    feedDeleteResponse.getResult().put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(feedDeleteResponse, self());
  }

  private void updateUserFeed(Request request, RequestContext context) {
    Map<String, Object> updateRequest = request.getRequest();
    String feedId = (String) updateRequest.get(JsonKey.FEED_ID);
    Feed feed = mapper.convertValue(updateRequest, Feed.class);
    feed.setId(feedId);
    feed.setStatus(FeedStatus.READ.getfeedStatus());
    Response feedUpdateResponse = feedService.update(feed, context);
    sender().tell(feedUpdateResponse, self());
  }
}
