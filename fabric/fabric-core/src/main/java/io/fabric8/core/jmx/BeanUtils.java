/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
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
package io.fabric8.core.jmx;

import io.fabric8.api.Container;
import io.fabric8.api.FabricException;
import io.fabric8.api.FabricService;
import io.fabric8.api.Ids;
import io.fabric8.api.Profile;
import io.fabric8.api.ProfileService;
import io.fabric8.api.Profiles;
import io.fabric8.api.Version;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.beanutils.PropertyUtils;
import io.fabric8.api.gravia.IllegalArgumentAssertion;

/**
 */
public class BeanUtils {

    private BeanUtils() {
        // Utils class
    }

    public static List<String> getFields(Class clazz) {
        List<String> answer = new ArrayList<String>();

        try {
            for (PropertyDescriptor desc : PropertyUtils.getPropertyDescriptors(clazz)) {
                if (desc.getReadMethod() != null) {
                    answer.add(desc.getName());
                }
            }
        } catch (Exception e) {
            throw new FabricException("Failed to get property descriptors for " + clazz.toString(), e);
        }

        // few tweaks to maintain compatibility with existing views for now...
        if (clazz.getSimpleName().equals("Container")) {
            answer.add("parentId");
            answer.add("versionId");
            answer.add("profileIds");
            answer.add("childrenIds");
            answer.remove("fabricService");
        } else if (clazz.getSimpleName().equals("Profile")) {
            answer.add("id");
            answer.add("parentIds");
            answer.add("childIds");
            answer.add("containerCount");
            answer.add("containers");
            answer.add("fileConfigurations");
        } else if (clazz.getSimpleName().equals("Version")) {
            answer.add("id");
            answer.add("defaultVersion");
        }

        return answer;
    }

    public static void setValue(Object instance, String property, Object value) {
        try {
            org.apache.commons.beanutils.BeanUtils.setProperty(instance, property, value);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set property " + property + " on " + instance.getClass().getName(), t);
        }
    }

    public static Object getValue(Object instance, String property) {
        try {
            return org.apache.commons.beanutils.BeanUtils.getProperty(instance, property);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set property " + property + " on " + instance.getClass().getName(), t);
        }
    }

    public static Map<String, Object> convertProfileToMap(FabricService fabricService, Profile profile, List<String> fields) {

        Map<String, Object> answer = new TreeMap<String, Object>();

        for (String field : fields) {

            if (field.equalsIgnoreCase("configurations") || field.equalsIgnoreCase("fileConfigurations")) {

                answer.put(field, fetchConfigurations(profile));

            } else if (field.equalsIgnoreCase("configurationFileNames")) {

                answer.put(field, fetchConfigurationFileNames(profile));

            } else if (field.equalsIgnoreCase("childIds")) {

                answer.put(field, fetchChildIds(fabricService, profile));

            } else if (field.equalsIgnoreCase("containers") || field.equalsIgnoreCase("associatedContainers")) {

                answer.put(field, fetchContainers(fabricService, profile));

            } else if (field.equalsIgnoreCase("containerCount")) {

                answer.put(field, fetchContainerCount(fabricService, profile));

            } else if (field.equalsIgnoreCase("parentIds") || field.equalsIgnoreCase("parents")) {

                answer.put(field, profile.getParentIds());

            } else if (field.equalsIgnoreCase("class") || field.equalsIgnoreCase("string")
                    || field.equalsIgnoreCase("abstractProfile") || field.equalsIgnoreCase("attributes")) {

                // ignore...

            } else {
                addProperty(profile, field, answer);
            }
        }

        return answer;
    }

    private static void addProperty(Object obj, String field, Map<String, Object> map) {
        try {
            Object prop = PropertyUtils.getProperty(obj, field);
            map.put(field, prop);
        } catch (Exception e) {
            throw new FabricException("Failed to initialize DTO", e);
        }
    }

    public static Map<String, Object> convertContainerToMap(FabricService fabricService, Container container, List<String> fields) {
        Map<String, Object> answer = new TreeMap<String, Object>();

        for (String field : fields) {

            if (field.equalsIgnoreCase("profiles") || field.equalsIgnoreCase("profileIds")) {

                answer.put(field, Ids.getIds(container.getProfiles()));

            } else if (field.equalsIgnoreCase("childrenIds") || field.equalsIgnoreCase("children")) {

                answer.put(field, Ids.getIds(container.getChildren()));

            } else if (field.equalsIgnoreCase("parent") || field.equalsIgnoreCase("parentId")) {

                answer.put(field, Ids.getId(container.getParent()));

            } else if (field.equalsIgnoreCase("version") || field.equalsIgnoreCase("versionId")) {

                answer.put(field, Ids.getId(container.getVersion()));

            } else if (field.equalsIgnoreCase("overlayProfile")) {

                Profile overlayProfile = container.getOverlayProfile();
                Profile effectiveProfile = Profiles.getEffectiveProfile(fabricService, overlayProfile);
                answer.put(field, convertProfileToMap(fabricService, effectiveProfile, getFields(Profile.class)));

            } else {
                addProperty(container, field, answer);
            }

        }

        return answer;
    }

    public static Map<String, Object> convertVersionToMap(FabricService fabricService, Version version, List<String> fields) {
        IllegalArgumentAssertion.assertNotNull(version, "version");
        IllegalArgumentAssertion.assertNotNull(fields, "fields");
        
        Map<String, Object> answer = new TreeMap<String, Object>();
        for (String field : fields) {
            if (field.equalsIgnoreCase("profiles") || field.equalsIgnoreCase("profileIds")) {
                answer.put(field, Ids.getIds(version.getProfiles()));
            } else if (field.equalsIgnoreCase("defaultVersion")) {
                answer.put(field, fabricService.getRequiredDefaultVersion().equals(version));
            } else if (field.equalsIgnoreCase("class") || field.equalsIgnoreCase("string")) {
                // ignore...
            } else {
                addProperty(version, field, answer);
            }
        }
        return answer;
    }

    public static List<String> fetchChildIds(FabricService fabricService, Profile self) {
        List<String> ids = new ArrayList<String>();
        ProfileService profileService = fabricService.adapt(ProfileService.class);
        for (Profile p : profileService.getRequiredVersion(self.getVersion()).getProfiles()) {
            for (String parentId : p.getParentIds()) {
                if (parentId.equals(self.getId())) {
                    ids.add(p.getId());
                    break;
                }
            }
        }
        return ids;
    }

    public static List<String> fetchContainers(FabricService fabricService, Profile self) {
        List<String> answer = new ArrayList<String>();
        String versionId = self.getVersion();
        String profileId = self.getId();
        for (Container c : fabricService.getAssociatedContainers(versionId, profileId)) {
            answer.add(c.getId());
        }
        return answer;
    }

    public static int fetchContainerCount(FabricService fabricService, Profile self) {
        String versionId = self.getVersion();
        String profileId = self.getId();
        return fabricService.getAssociatedContainers(versionId, profileId).length;
    }

    public static List<String> fetchConfigurations(Profile self) {
        List<String> answer = new ArrayList<String>();
        answer.addAll(self.getFileConfigurations().keySet());
        return answer;
    }

    public static List<String> fetchConfigurationFileNames(Profile self) {
        List<String> answer = new ArrayList<String>();
        answer.addAll(self.getConfigurationFileNames());
        return answer;
    }

    public static List<String> collapseToList(List<Map<String, Object>> objs, String field) {
        List<String> answer = new ArrayList<String>();
        for (Map<String, Object> o : objs) {
            answer.add(o.get(field).toString());
        }
        return answer;
    }

}
