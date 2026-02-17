package com.example.jms;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.RequestScoped;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Enumeration;
import java.util.logging.Logger;

@Path("/messages")
@RequestScoped
public class JmsProducerResource {

    private static final Logger LOG = Logger.getLogger(JmsProducerResource.class.getName());

    @Resource(lookup = "jms/SimpleConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = "jms/SimpleQueue")
    private Queue queue;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendMessage(String message) {
        try (JMSContext context = connectionFactory.createContext()) {
            context.createProducer().send(queue, message);
            LOG.info("Message sent to queue: " + message);
            return Response.ok("{\"status\":\"Message sent successfully\",\"message\":\"" + message + "\"}")
                           .build();
        } catch (Exception e) {
            LOG.severe("Failed to send message: " + e.getMessage());
            return Response.serverError()
                           .entity("{\"status\":\"error\",\"detail\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    @GET
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQueueDepth() {
        try (JMSContext context = connectionFactory.createContext()) {
            QueueBrowser browser = context.createBrowser(queue);
            Enumeration<?> messages = browser.getEnumeration();
            int count = 0;
            while (messages.hasMoreElements()) {
                messages.nextElement();
                count++;
            }
            return Response.ok("{\"queue\":\"simpleQueue\",\"depth\":" + count + "}").build();
        } catch (Exception e) {
            LOG.severe("Failed to browse queue: " + e.getMessage());
            return Response.serverError()
                           .entity("{\"status\":\"error\",\"detail\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }
}
