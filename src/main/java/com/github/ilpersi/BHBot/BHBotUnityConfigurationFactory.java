package com.github.ilpersi.BHBot;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;

import java.net.URI;

@Plugin(name = "com.github.ilpersi.BHBot.BHBotConfigurationFactory", category = ConfigurationFactory.CATEGORY)
public class BHBotUnityConfigurationFactory extends ConfigurationFactory {

    private static Configuration createConfiguration(final String name, ConfigurationBuilder<BuiltConfiguration> builder) {
        System.setProperty("log4j.skipJansi", "false");

        builder.setConfigurationName(name);
        builder.setStatusLevel(Level.ERROR);

        // baseDir for logs
        builder.addProperty("baseDir", BHBotUnity.logBaseDir);

        // Disable log entries for io.netty
        LoggerComponentBuilder nettyDisabler = builder.newLogger("io.netty", Level.WARN);
        builder.add(nettyDisabler);

        // Disable log entries for org.asynchttpclient.netty
        LoggerComponentBuilder nettyDisabler2 = builder.newLogger("org.asynchttpclient.netty", Level.WARN);
        builder.add(nettyDisabler2);

        // STD OUT
        AppenderComponentBuilder stdOutBuilder = builder.newAppender("StdOut", "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
        stdOutBuilder.add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d{ABSOLUTE} %highlight{%level}{FATAL=bg_red, ERROR=bg_red, WARN=bg_yellow, AUTOBRIBE=cyan, AUTOREVIVE=cyan, AUTOSHRINE=cyan, AUTORUNE=cyan, STATS=magenta, READOUT=yellow, INFO=green, DEBUG=blue} - %msg%n")
                // If you need to know the class triggering the log message, just replace the previous line with this one
                //.addAttribute("pattern", "%logger %d{HH:mm:ss.SSS} %p %m %ex%n")
                .addAttribute("disableAnsi", "false"));
        stdOutBuilder.add(builder.newFilter("ThresholdFilter", Filter.Result.DENY,
                Filter.Result.ACCEPT).addAttribute("level", Level.ERROR));
        builder.add(stdOutBuilder);

        // STD ERR
        AppenderComponentBuilder stdErrBuilder = builder.newAppender("StdErr", "CONSOLE").
                addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR);
        stdErrBuilder.add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d{ABSOLUTE} %style{%highlight{%level}{FATAL=bg_red, ERROR=red, WARN=bg_yellow, AUTOBRIBE=cyan, AUTOREVIVE=cyan, AUTOSHRINE=cyan, AUTORUNE=cyan, STATS=magenta, READOUT=yellow, INFO=green, DEBUG=blue} - %msg%n}{red}")
                .addAttribute("disableAnsi", "false"));
        stdErrBuilder.add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT,
                Filter.Result.DENY).addAttribute("level", Level.ERROR));
        builder.add(stdErrBuilder);

        // Rolling File
        // Composite Triggering Policies
        @SuppressWarnings("rawtypes")
        ComponentBuilder triggeringPolicy = builder.newComponent("Policies")
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", "32M"))
                .addComponent(builder.newComponent("TimeBasedTriggeringPolicy"));


        // Delete Component to manage old log deletion
        @SuppressWarnings("rawtypes")
        ComponentBuilder delete = builder.newComponent("Delete")
                .addAttribute("basePath", "${baseDir}")
                .addAttribute("maxDepth", "2")
                //.addAttribute("testMode", true)
                .addComponent(builder.newComponent("IfLastModified")
                        .addAttribute("age", "" + BHBotUnity.logMaxDays + "d"));

        // DefaultRolloverStrategy Component
        @SuppressWarnings("rawtypes")
        ComponentBuilder defaulRolloverStrategy = builder.newComponent("DefaultRolloverStrategy")
                .addAttribute("max", BHBotUnity.logMaxDays)
                .addAttribute("compressionLevel", 9)
                .addComponent(delete);

        AppenderComponentBuilder rollingBuilder = builder.newAppender("Rolling", "RollingFile")
                .addAttribute("filePattern", "${baseDir}/$${date:yyyy-MM}/BHBot-%d{yyyy-MM-dd}-%i.log.zip")
                .addAttribute("fileName", "${baseDir}/bhbot.log");
        rollingBuilder.add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d [%t] %p %c - %m%n"));
        rollingBuilder
                .addComponent(triggeringPolicy)
                .addComponent(defaulRolloverStrategy);
        builder.add(rollingBuilder);


        builder.add(
                builder.newRootLogger(BHBotUnity.logLevel)
                        .add(builder.newAppenderRef("StdOut"))
                        .add(builder.newAppenderRef("StdErr"))
                        .add(builder.newAppenderRef("Rolling"))
        );
        return builder.build();
    }

    @Override
    public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
        return getConfiguration(loggerContext, source.toString(), null);
    }

    @Override
    public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation) {
        ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
        return createConfiguration(name, builder);
    }

    @Override
    protected String[] getSupportedTypes() {
        return new String[]{
                "*"
        };
    }
}