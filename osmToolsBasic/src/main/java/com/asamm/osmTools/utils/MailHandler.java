/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

import java.io.File;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 *
 * @author volda
 */
public class MailHandler {
    private String SMTP_HOST_NAME;
    private int SMTP_HOST_PORT;
    private String SMTP_AUTH_USER;
    private String SMTP_AUTH_PWD;

    public MailHandler() {
        SMTP_HOST_NAME = "smtp.gmail.com";
        SMTP_HOST_PORT = 465;
        SMTP_AUTH_USER = "petr.voldan@asamm.com";
        SMTP_AUTH_PWD  = "Babi4ka1530";
    
    }
    
    public void sendEmail(String emailTo, String subject, String text, String pathToAttachment) throws Exception{
        

        try {

           Properties props = new Properties();
 
            props.put("mail.transport.protocol", "smtps");
            props.put("mail.smtps.host", SMTP_HOST_NAME);
            props.put("mail.smtps.auth", "true");
            // props.put("mail.smtps.quitwait", "false");

            
            Session mailSession = Session.getDefaultInstance(props);
            //mailSession.setDebug(true);
            Transport transport = mailSession.getTransport();

            MimeMessage message = new MimeMessage(mailSession);
            // message subject
            message.setSubject(subject);
            // message body
           // message.setContent(text, "text/plain");

            message.addRecipient(Message.RecipientType.TO,
                 new InternetAddress(emailTo));
            

            // create the message part 
            MimeBodyPart messageBodyPart = new MimeBodyPart();

            //fill message
            messageBodyPart.setText(text);

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);

            // Part two is attachment
            File attachedFile = new File(pathToAttachment);
            if (attachedFile.exists()){
                messageBodyPart = new MimeBodyPart();
                DataSource source = new FileDataSource(new File(pathToAttachment));
                messageBodyPart.setDataHandler(new DataHandler(source));
                messageBodyPart.setFileName(attachedFile.getName());
                multipart.addBodyPart(messageBodyPart);
                
            }           

            // Put parts in message
            message.setContent(multipart);

            transport.connect
              (SMTP_HOST_NAME, SMTP_HOST_PORT, SMTP_AUTH_USER, SMTP_AUTH_PWD);

            transport.sendMessage(message,
                message.getRecipients(Message.RecipientType.TO));
            transport.close();
        
        } catch (MessagingException e) {
            System.out.println("Error during sending email");
            throw new RuntimeException(e);
        }
    }

    
    
}
