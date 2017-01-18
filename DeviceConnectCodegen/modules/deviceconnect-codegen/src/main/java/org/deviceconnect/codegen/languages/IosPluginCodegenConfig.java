package org.deviceconnect.codegen.languages;


import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IosPluginCodegenConfig extends AbstractPluginCodegenConfig {

    @Override
    public void processOpts() {
        super.processOpts();
        embeddedTemplateDir = templateDir = getName();

        // README
        supportingFiles.add(new SupportingFile("README.md.mustache", "", "README.md"));

        String classPrefix = getClassPrefix();
        additionalProperties.put("serviceName", classPrefix + " Service");
        additionalProperties.put("serviceId", classPrefix.toLowerCase() + "_service_id");
        String pluginClass = classPrefix + "Plugin";
        additionalProperties.put("pluginClass", pluginClass);
        additionalProperties.put("basePluginClass", "DConnectDevicePlugin");

        String classesDir = "Classes";
        supportingFiles.add(new SupportingFile("plugin.h.mustache", classesDir, pluginClass + ".h"));
        supportingFiles.add(new SupportingFile("plugin.m.mustache", classesDir, pluginClass + ".m"));
    }

    @Override
    protected String profileFileFolder() {
        return outputFolder + File.separator + "Classes" + File.separator + "Profiles";
    }

    @Override
    protected List<ProfileTemplate> prepareProfileTemplates(final String profileName, final Map<String, Object> properties) {
        final List<ProfileTemplate> profileTemplates = new ArrayList<>();
        final String profileClass = getClassPrefix() + toUpperCapital(profileName) + "Profile";
        properties.put("baseProfileClass", "DConnect" + toUpperCapital(profileName) + "Profile");
        properties.put("profileClass", profileClass);

        ProfileTemplate header = new ProfileTemplate();
        header.templateFile = "profile.h.mustache";
        header.outputFile = getClassPrefix() + toUpperCapital(profileName) + "Profile.h";
        profileTemplates.add(header);
        ProfileTemplate impl = new ProfileTemplate();
        impl.templateFile = "profile.m.mustache";
        impl.outputFile = getClassPrefix() + toUpperCapital(profileName) + "Profile.m";
        profileTemplates.add(impl);

        ((List<Object>) additionalProperties.get("supportedProfileNames")).add(new Object() { String name = profileName; });
        ((List<Object>) additionalProperties.get("supportedProfileClasses")).add(new Object() { String name = profileClass; });

        return profileTemplates;
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.OTHER;
    }

    @Override
    public String getName() {
        return "deviceConnectIosPlugin";
    }

    @Override
    public String getHelp() {
        return "Generates a stub of Device Connect Plug-in for iOS.";
    }
}