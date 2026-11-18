package dev.bpmcrafters.example.quarkus.adapter.in.rest;

import dev.bpmcrafters.example.quarkus.adapter.NotificationMapper;
import dev.bpmcrafters.example.quarkus.application.port.in.ManageNotifications;
import dev.bpmcrafters.example.quarkus.domain.Notification.Type;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class NotificationResource {
  private final ManageNotifications notifications;

  public NotificationResource(ManageNotifications notifications) { this.notifications = notifications; }

  @GET
  @Path("user-tasks")
  public List<NotificationMapper.Summary> tasks() {
    return notifications.list().stream().map(NotificationMapper::summary).toList();
  }

  @GET
  @Path("failed-payment/{taskId}")
  public NotificationMapper.Details failed(@PathParam("taskId") String id) { return details(id, Type.FAILED_PAYMENT); }

  @GET
  @Path("delayed-inventory/{taskId}")
  public NotificationMapper.Details delayed(@PathParam("taskId") String id) { return details(id, Type.DELAYED_INVENTORY); }

  @POST
  @Path("failed-payment/{taskId}")
  public Response completeFailed(@PathParam("taskId") String id) throws Exception { return complete(id, Type.FAILED_PAYMENT); }

  @POST
  @Path("delayed-inventory/{taskId}")
  public Response completeDelayed(@PathParam("taskId") String id) throws Exception { return complete(id, Type.DELAYED_INVENTORY); }

  private NotificationMapper.Details details(String id, Type type) {
    return notifications.find(new ManageNotifications.Command(id, type)).map(NotificationMapper::details).orElseThrow(NotFoundException::new);
  }

  private Response complete(String id, Type type) throws Exception {
    if (!notifications.complete(new ManageNotifications.Command(id, type))) throw new NotFoundException();
    return Response.noContent().build();
  }
}
