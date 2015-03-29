package com.icegreen.greenmail.webapp;

import com.icegreen.greenmail.Managers;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.user.UserException;
import com.icegreen.greenmail.util.Service;
import com.icegreen.greenmail.webapp.Configuration.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically starts and stops GreenMail server upon deployment/undeployment.
 *
 * @author mm
 */
public class GreenMailListener implements ServletContextListener {
    private Logger log = LoggerFactory.getLogger(GreenMailListener.class);
    private Managers managers;
    private List<Service> services;
    private Configuration configuration;

    public void contextInitialized(final ServletContextEvent sce) {
        log.info("Initializing GreenMail");

        managers = new Managers();
        ServletContext ctx = sce.getServletContext();
        configuration = ConfigurationFactory.create(extractParameters(ctx));
        services = ServiceFactory.create(configuration, managers);

        for (Configuration.User user : configuration.getUsers()) {
            GreenMailUser greenMailUser = managers.getUserManager().getUser(user.email);
            if (null == greenMailUser) {
                try {
                    greenMailUser = managers.getUserManager().createUser(
                            user.email, user.login, user.password);
                    greenMailUser.setPassword(user.password);
                } catch (UserException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        for (Service s : services) {
            log.info("Starting GreenMail service: " + s.toString());
            s.startService(null);
        }

        try {
            File hc = File.createTempFile("hupa-config", ".properties");
            BufferedWriter hcOut = new BufferedWriter(new FileWriter(hc));
            for (ServiceConfiguration sc : configuration.getServiceConfigurations()) {
                hcOut.write(sc.protocol.name() + "ServerAddress = " + sc.hostname + "\n");
                hcOut.write(sc.protocol.name() + "ServerPort = " + sc.port + "\n");
            }
            hcOut.close();
            System.setProperty("hupa.config.file", hc.getAbsolutePath());
        }
        catch (IOException ignore) {
            ignore.printStackTrace();
        }
    }

    public void contextDestroyed(final ServletContextEvent sce) {
        log.info("Destroying GreenMail WebApp");
        for (Service s : services) {
            log.info("Stopping GreenMail service: " + s.toString());
            s.stopService(null);
        }
    }

    private Map<String, String> extractParameters(ServletContext pServletContext) {
        Enumeration names = pServletContext.getInitParameterNames();
        Map<String, String> parameterMap = new HashMap<String, String>();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            parameterMap.put(name, pServletContext.getInitParameter(name));
        }
        return parameterMap;
    }

    public Managers getManagers() {
        return managers;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public List<Service> getServices() {
        return services;
    }
}
