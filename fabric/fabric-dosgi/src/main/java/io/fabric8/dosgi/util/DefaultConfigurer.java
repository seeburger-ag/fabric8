/*
 * DefaultConfigurer.java
 *
 * created at 05.02.2016 by utzig <j.utzig@seeburger.de>
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package io.fabric8.dosgi.util;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class DefaultConfigurer implements Configurer
{

    @Override
    public <T> Map<String, ? > configure(Map<String, ? > configuration, T target, String... ignorePrefix) throws Exception
    {
        // TODO Stub implementation
        return configuration;
    }


    @Override
    public <T> Map<String, ? > configure(Dictionary<String, ? > configuration, T target, String... ignorePrefix) throws Exception
    {
        // TODO Stub implementation
        Map result = new HashMap();

        Enumeration<String> keys = configuration.keys();
        while (keys.hasMoreElements())
        {
            String key = (String)keys.nextElement();
            result.put(key, configuration.get(key));

        }
        return result;
    }

}



