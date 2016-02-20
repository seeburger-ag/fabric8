/**
 *  Copyright 2016 SEEBURGER AG
 *
 *  SEEBURGER licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package com.seeburger.ecf.util;

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



