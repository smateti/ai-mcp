package com.example.jms;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import java.util.logging.Logger;

@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/SimpleQueue"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class JmsConsumerMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(JmsConsumerMDB.class.getName());

    @Inject
    private MdbControl mdbControl;

    @Override
    public void onMessage(Message message) {
        try {
            // Block here if activation spec is paused
            mdbControl.waitIfPaused();

            if (message instanceof TextMessage textMessage) {
                String text = textMessage.getText();
                LOG.info("=== MDB Received message from queue: " + text + " ===");
                // Simulate processing delay (200-500ms) mimicking real business logic
                Thread.sleep(200 + (long) (Math.random() * 300));
            } else {
                LOG.warning("Received non-text message of type: " + message.getClass().getName());
            }
        } catch (JMSException | InterruptedException e) {
            LOG.severe("Error processing JMS message: " + e.getMessage());
        }
    }
}
