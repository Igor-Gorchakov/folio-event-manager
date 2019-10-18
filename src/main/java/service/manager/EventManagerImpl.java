package service.manager;

import io.vertx.core.Future;
import model.events.Context;
import model.profiles.ProfileSnapshotWrapper;
import service.handlers.EventHandler;
import service.processor.EventProcessor;
import service.processor.EventProcessorImpl;

import java.util.List;
import java.util.Optional;

public class EventManagerImpl implements EventManager {
    private EventProcessor eventProcessor;

    public EventManagerImpl(List<EventHandler> eventHandlers) {
        this.eventProcessor = new EventProcessorImpl(eventHandlers);
    }

    @Override
    public Future<Context> handleEvent(Context context) {
        Future<Context> future = Future.future();
        eventProcessor.process(context).setHandler(firstAr -> {
            if (firstAr.failed()) {
                future.fail(firstAr.cause());
            } else {
                Context firstContext = firstAr.result();
                if (firstContext == null) {
                    future.complete();
                } else {
                    processEventRecursively(firstContext).setHandler(nextAr -> {
                        if (nextAr.failed()) {
                            future.fail(nextAr.cause());
                        } else {
                            Context lastContext = nextAr.result();
                            if (lastContext == null) {
                                future.complete();
                            } else {
                                future.complete(prepareLastContext(lastContext));
                            }
                        }
                    });
                }
            }
        });
        return future;
    }

    private Future<Context> processEventRecursively(Context context) {
        Future<Context> future = Future.future();
        eventProcessor.process(context).setHandler(ar -> {
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                Context nextContext = ar.result();
                if (nextContext == null) {
                    future.complete(context);
                } else {
                    processEventRecursively(nextContext).setHandler(future);
                }
            }
        });
        return future;
    }

    private Context prepareLastContext(Context context) {
        ProfileSnapshotWrapper snapshotWrapper = context.getProfileSnapshot();
        List<String> currentNodePath = context.getCurrentNodePath();
        Optional<ProfileSnapshotWrapper> optionalNextNode = findSnapshotWrapperByPath(snapshotWrapper, currentNodePath);
        if (optionalNextNode.isPresent()) {
            ProfileSnapshotWrapper nextNode = optionalNextNode.get();
            context.setCurrentNode(nextNode);
            currentNodePath.add(nextNode.getId());
            context.setCurrentNodePath(currentNodePath);
            return context;
        } else {
            // No next node, all profiles are processed, nothing to publish
            return null;
        }
    }

    private Optional<ProfileSnapshotWrapper> findSnapshotWrapperByPath(ProfileSnapshotWrapper currentWrapper, List<String> path) {
        Optional<ProfileSnapshotWrapper> profileSnapshotWrapper = Optional.of(currentWrapper);
        return profileSnapshotWrapper;
    }
}