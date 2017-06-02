package io.swagger.parser.v3.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.oas.models.ExternalDocumentation;
import io.swagger.oas.models.OpenAPI;
import io.swagger.oas.models.Operation;
import io.swagger.oas.models.PathItem;
import io.swagger.oas.models.Paths;
import io.swagger.oas.models.callbacks.Callback;
import io.swagger.oas.models.callbacks.Callbacks;
import io.swagger.oas.models.examples.Example;
import io.swagger.oas.models.links.Link;
import io.swagger.oas.models.info.Contact;
import io.swagger.oas.models.info.Info;
import io.swagger.oas.models.media.AllOfSchema;
import io.swagger.oas.models.media.Content;
import io.swagger.oas.models.media.Encoding;
import io.swagger.oas.models.media.MediaType;
import io.swagger.oas.models.media.Schema;
import io.swagger.oas.models.media.XML;
import io.swagger.oas.models.tags.Tag;
import io.swagger.oas.models.info.License;
import io.swagger.oas.models.media.EncodingProperty;
import io.swagger.oas.models.headers.Header;
import io.swagger.oas.models.parameters.CookieParameter;
import io.swagger.oas.models.parameters.HeaderParameter;
import io.swagger.oas.models.parameters.Parameter;
import io.swagger.oas.models.parameters.PathParameter;
import io.swagger.oas.models.parameters.QueryParameter;
import io.swagger.oas.models.parameters.RequestBody;
import io.swagger.oas.models.responses.ApiResponse;
import io.swagger.oas.models.responses.ApiResponses;
import io.swagger.oas.models.security.SecurityRequirement;
import io.swagger.oas.models.servers.Server;
import io.swagger.oas.models.servers.ServerVariable;
import io.swagger.oas.models.servers.ServerVariables;
import io.swagger.parser.models.SwaggerParseResult;
import io.swagger.util.Json;
import org.apache.commons.lang3.StringUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class OpenAPIDeserializer {

    protected static Set<String> ROOT_KEYS = new LinkedHashSet<>(Arrays.asList("openapi", "info", "host", "paths", "parameters", "responses", "security", "tags", "externalDocs"));
    protected static Set<String> INFO_KEYS = new LinkedHashSet<>(Arrays.asList("title", "description", "termsOfService", "contact", "license", "version"));
    protected static Set<String> CONTACT_KEYS = new LinkedHashSet<>(Arrays.asList("name", "url", "email"));
    protected static Set<String> LICENSE_KEYS = new LinkedHashSet<>(Arrays.asList("name", "url"));
    protected static Set<String> OPERATION_KEYS = new LinkedHashSet<>(Arrays.asList("scheme", "tags", "summary", "description", "externalDocs", "operationId", "parameters", "responses", "schemes", "deprecated", "security"));
    protected static Set<String> PATH_KEYS = new LinkedHashSet<>(Arrays.asList("$ref", "get", "put", "post", "delete", "head", "patch", "options", "parameters"));
    protected static Set<String> EXTERNAL_DOCS_KEYS = new LinkedHashSet<>(Arrays.asList("description", "url"));
    protected static Set<String> RESPONSE_KEYS = new LinkedHashSet<>(Arrays.asList("description", "schema", "headers", "examples"));
    private static final String QUERY_PARAMETER = "query";
    private static final String COOKIE_PARAMETER = "cookie";
    private static final String PATH_PARAMETER = "path";
    private static final String HEADER_PARAMETER = "header";

    public SwaggerParseResult deserialize(JsonNode rootNode) {
        SwaggerParseResult result = new SwaggerParseResult();
        try {
            // TODO
            ParseResult rootParse = new ParseResult();
            OpenAPI api = parseRoot(rootNode, rootParse);
            result.setOpenAPI(api);
            result.setMessages(rootParse.getMessages());
        } catch (Exception e) {
            result.setMessages(Arrays.asList(e.getMessage()));

        }
        return result;
    }

    public OpenAPI parseRoot(JsonNode node, ParseResult result) {
        String location = "";
        OpenAPI openAPI = new OpenAPI();
        if (node.getNodeType().equals(JsonNodeType.OBJECT)) {
            ObjectNode rootNode = (ObjectNode) node;

            // required
            String value = getString("openapi", rootNode, true, location, result);

            // we don't even try if the version isn't there
            if(value == null || !value.startsWith("3.0")) {
                return null;
            }
            openAPI.setOpenapi(value);

            ObjectNode obj = getObject("info", rootNode, true, "", result);
            if (obj != null) {
                Info info = getInfo(obj, "info", result);
                openAPI.setInfo(info);
            }

            obj = getObject("paths", rootNode, true, location, result);
            if (obj != null) {
                Paths paths = getPaths(obj, "paths", result);
                openAPI.setPaths(paths);
            }

            ArrayNode array = getArray("servers", rootNode, false, location, result);
            if (array != null) {
                openAPI.setServers(getServersList(array, location, result));
            }

            obj = getObject("externalDocs", rootNode, false, location, result);
            if (obj != null) {
                ExternalDocumentation externalDocs = getExternalDocs(obj, "externalDocs", result);
                openAPI.setExternalDocs(externalDocs);
            }

            array = getArray("tags", rootNode, false, location, result);
            if (array != null) {
                openAPI.setTags(getTagList(array, location, result));
            }

            array = getArray("security", rootNode, false, location, result);
            if (array != null) {
                openAPI.setSecurity(getSecurityRequirementsList(array, "security", result));
            }
            openAPI.setExtensions(new LinkedHashMap<>());

            Set<String> keys = getKeys(rootNode);
            for(String key : keys) {
                if(key.startsWith("x-")) {
                    openAPI.getExtensions().put(key, Json.mapper().convertValue(rootNode.get(key), Object.class));
                }
                else if(!ROOT_KEYS.contains(key)) {
                    result.extra(location, key, rootNode.get(key));
                }
            }

        } else {
            result.invalidType("", "", "object", node);
            result.invalid();
            return null;
        }


        return openAPI;
    }

    public List<Tag> getTagList(ArrayNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }
        List<Tag> tags = new ArrayList<>();
        for (JsonNode item : obj) {
            if (item.getNodeType().equals(JsonNodeType.OBJECT)) {
                Tag tag = getTag((ObjectNode) item, location, result);
                if (tag != null) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    public Tag getTag(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }

        Tag tag = new Tag();

        String value = getString("name", obj, true, location, result);
        tag.setName(value);

        value = getString("description", obj, false, location, result);
        tag.setDescription(value);

        Set<String> tagKeys = getKeys(obj);
        for (String tagName : tagKeys) {
            JsonNode tagValue = obj.get(tagName);
            if (!tagValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, tagName, "object", tagValue);
            } else {
                ObjectNode tagObj = (ObjectNode) tagValue;
                ExternalDocumentation externalDocs = getExternalDocs(tagObj, location + ".'" + tagName + "'", result);
                tag.setExternalDocs(externalDocs);
            }
        }


        return tag;
    }



    public List<Server> getServersList(ArrayNode obj, String location, ParseResult result) {

        List<Server> servers = new ArrayList<>();
        if (obj == null) {
            return null;

        }
        for (JsonNode item : obj) {
            if (item.getNodeType().equals(JsonNodeType.OBJECT)) {
                Server server = getServer((ObjectNode) item, location, result);
                if (server != null) {
                    servers.add(server);
                }
            }
        }
        return servers;
    }

    public Server getServer(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }

        Server server = new Server();

        String value = getString("url", obj, true, location, result);
        server.setUrl(value);

        value = getString("description", obj, false, location, result);
        server.setDescription(value);



        server.setVariables(getServerVariables(obj,location,result));



        return server;
    }

    public ServerVariables getServerVariables(ObjectNode obj, String location, ParseResult result){
        ServerVariables serverVariables = new ServerVariables();
        if (obj == null) {
            return null;
        }

        Set<String> serverKeys = getKeys(obj);
        for (String serverName : serverKeys) {
            JsonNode serverValue = obj.get(serverName);
            if (!serverValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, serverName, "object", serverValue);
            } else {
                ObjectNode server = (ObjectNode) serverValue;
                serverVariables = getServerVariable(server, location + ".'" + serverName + "'", result);
            }
        }

        return serverVariables;
    }

    public ServerVariables getServerVariable(ObjectNode obj, String location, ParseResult result){
        if(obj == null){
            return null;
        }
        final ServerVariables serverVariables = new ServerVariables();
        ServerVariable serverVariable = null;

        Set<String> variableKeys = getKeys(obj);
        for (String variableName : variableKeys) {
            JsonNode variableValue = obj.get(variableName);
            if (!variableValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, variableName, "object", variableValue);
            } else {
                serverVariable = new ServerVariable();
                ObjectNode objNode = getObject(variableName, obj, false, location, result);

                ArrayNode an = getArray("enum",objNode,false,location,result);
                if (an != null) {
                    List<String> _enum = new ArrayList<>();
                    for(JsonNode n : an) {
                        if(n.isValueNode()) {
                            _enum.add(n.asText());
                            serverVariable.setEnum(_enum);
                        }
                        else {
                            result.invalidType(location, "enum", "value", n);
                        }
                    }
                }
                String value = getString("default", objNode, true, location + ".'" + variableName + "'", result);
                serverVariable.setDefault(value);

                value = getString("description", objNode, false, location + ".'" + variableName + "'", result);
                serverVariable.setDescription(value);
                serverVariables.addServerVariable(variableName,serverVariable);
            }
        }
        return serverVariables;
    }

    //PathsObject

    public Paths getPaths(ObjectNode obj, String location, ParseResult result) {
        final Paths paths = new Paths();
        if (obj == null) {
            return null;
        }
        Set<String> pathKeys = getKeys(obj);
        for (String pathName : pathKeys) {
            JsonNode pathValue = obj.get(pathName);
            if (!pathValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, pathName, "object", pathValue);
            } else {
                ObjectNode path = (ObjectNode) pathValue;
                PathItem pathObj = getPathItem(path, location + ".'" + pathName + "'", result);
                paths.put(pathName, pathObj);
            }
        }
        return paths;
    }

    public PathItem getPathItem(ObjectNode obj, String location, ParseResult result) {
        boolean hasRef = false;

        if (obj.get("$ref") != null) {
            JsonNode ref = obj.get("$ref");

            if (ref.getNodeType().equals(JsonNodeType.STRING)) {
                //return pathRef((TextNode)ref, location, result);
            } else if (ref.getNodeType().equals(JsonNodeType.OBJECT)) {
                ObjectNode node = (ObjectNode) ref;

                // extra keys
                Set<String> keys = getKeys(node);
                for (String key : keys) {
                    result.extra(location, key, node.get(key));
                }
            }
            return null;
        }
        PathItem pathItem = new PathItem();

        String value = getString("summary", obj, true, location, result);
        pathItem.setSummary(value);

        value = getString("description", obj, false, location, result);
        pathItem.setDescription(value);

        ArrayNode parameters = getArray("parameters", obj, false, location, result);
        pathItem.setParameters(getParameterList(parameters, location, result));

        ArrayNode servers = getArray("servers", obj, false, location, result);
        pathItem.setServers(getServersList(servers, location, result));

        ObjectNode node = getObject("get", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(get)", result);
            if (operation != null) {
                pathItem.setGet(operation);
            }
        }
        node = getObject("put", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(put)", result);
            if (operation != null) {
                pathItem.setPut(operation);
            }
        }
        node = getObject("post", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(post)", result);
            if (operation != null) {
                pathItem.setPost(operation);
            }
        }
        node = getObject("head", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(head)", result);
            if (operation != null) {
                pathItem.setHead(operation);
            }
        }
        node = getObject("delete", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(delete)", result);
            if (operation != null) {
                pathItem.setDelete(operation);
            }
        }
        node = getObject("patch", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(patch)", result);
            if (operation != null) {
                pathItem.setPatch(operation);
            }
        }
        node = getObject("options", obj, false, location, result);
        if (node != null) {
            Operation operation = getOperation(node, location + "(options)", result);
            if (operation != null) {
                pathItem.setOptions(operation);
            }
        }

        // extra keys
        Set<String> keys = getKeys(obj);
        for (String key : keys) {
            if (key.startsWith("x-")) {
                //createPathItem.setVendorExtension(key, extension(obj.get(key)));
            } else if (!PATH_KEYS.contains(key)) {
                result.extra(location, key, obj.get(key));
            }
        }
        return pathItem;
    }


    public ExternalDocumentation getExternalDocs(ObjectNode node, String location, ParseResult result) {
        ExternalDocumentation externalDocs = null;

        if (node != null) {
            externalDocs = new ExternalDocumentation();
            Set<String> keys = getKeys(node);

            String value = getString("description", node, false, location, result);
            externalDocs.description(value);

            value = getString("url", node, true, location, result);
            externalDocs.url(value);

            // extra keys
            for (String key : keys) {
                if (key.startsWith("x-")) {
                    //output.setVendorExtension(key, extension(node.get(key)));
                } else if (!EXTERNAL_DOCS_KEYS.contains(key)) {
                    result.extra(location + ".externalDocs", key, node.get(key));
                }
            }
        }

        return externalDocs;
    }


    public String getString(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        String value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!v.isValueNode()) {
            result.invalidType(location, key, "string", node);
        } else {
            value = v.asText();
        }
        return value;
    }

    public Set<String> getKeys(ObjectNode node) {
        Set<String> keys = new LinkedHashSet<>();
        if (node == null) {
            return keys;
        }

        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            keys.add(it.next());
        }

        return keys;
    }


    public ObjectNode getObject(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        JsonNode value = node.get(key);
        ObjectNode object = null;
        if (value == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!value.getNodeType().equals(JsonNodeType.OBJECT)) {
            result.invalidType(location, key, "object", value);
            if (required) {
                result.invalid();
            }
        } else {
            object = (ObjectNode) value;
        }
        return object;
    }

    public Info getInfo(ObjectNode node, String location, ParseResult result) {
        if (node == null)
            return null;

        Info info = new Info();
        String value = getString("title", node, true, location, result);
        info.setTitle(value);

        value = getString("description", node, false, location, result);
        info.setDescription(value);

        value = getString("termsOfService", node, false, location, result);
        info.setTermsOfService(value);

        ObjectNode obj = getObject("contact", node, false, "contact", result);
        Contact contact = getContact(obj, location, result);
        info.setContact(contact);

        obj = getObject("license", node, false, location, result);
        License license = getLicense(obj, location, result);
        info.setLicense(license);

        value = getString("version", node, true, location, result);
        info.setVersion(value);

        // extra keys
        Set<String> keys = getKeys(node);
        for (String key : keys) {
            if (key.startsWith("x-")) {
                //info.setVendorExtension(key, extension(node.get(key)));
            } else if (!INFO_KEYS.contains(key)) {
                result.extra(location, key, node.get(key));
            }
        }

        return info;
    }

    public Content getContent(ObjectNode node, String location, ParseResult result){
        if (node == null) {
            return null;
        }
        Content content = new Content();
        Set<String> keys = getKeys(node);
        for(String key : keys) {
            MediaType mediaType = getMediaType((ObjectNode) node.get(key), location, result);
            if (mediaType != null) {
                content.addMediaType(key, mediaType);
            }
        }

        return content;
    }

    public MediaType getMediaType(ObjectNode contentNode, String location, ParseResult result){
        if (contentNode == null) {
            return null;
        }
        MediaType mediaType = new MediaType();

        ObjectNode schemaObject = getObject("schema",contentNode,false,location,result);
        if(schemaObject!=null){
            mediaType.setSchema(getSchema(schemaObject,location,result));
        }

        String value = getString("example", contentNode, false, location, result);
        mediaType.setExample(value);

        ObjectNode examplesObject = getObject("examples",contentNode,false,location,result);
        if(examplesObject!=null) {
            mediaType.setExamples(getExamples(examplesObject, location, result));
        }

        ObjectNode encodingObject = getObject("encoding",contentNode,false,location,result);
        if(encodingObject!=null) {
            mediaType.setEncoding(getEncoding(encodingObject, location, result));
        }

        return mediaType;
    }

    public Encoding getEncoding(ObjectNode node, String location, ParseResult result){
        if (node == null) {
            return null;
        }
        Encoding encoding = new Encoding();
        Set<String> keys = getKeys(node);
        for(String key : keys) {
            EncodingProperty encodingProperty = getEncodingProperty((ObjectNode) node.get(key), location, result);
            if (encodingProperty != null) {
                encoding.addEncodingProperty(key, encodingProperty);
            }
        }
        return encoding;
    }

    public EncodingProperty getEncodingProperty(ObjectNode node, String location, ParseResult result) {
        if (node == null) {
            return null;
        }

        EncodingProperty encodingProperty = new EncodingProperty();

        String value = getString("contentType", node, true, location, result);
        encodingProperty.setContentType(value);

        value = getString("style", node, true, location, result);

        if (StringUtils.isBlank(value)) {
            encodingProperty.setStyle(EncodingProperty.StyleEnum.FORM);
        } else {
            if (value.equals(Parameter.StyleEnum.FORM.toString())) {
                encodingProperty.setStyle(EncodingProperty.StyleEnum.FORM);
            } else if (value.equals(EncodingProperty.StyleEnum.DEEPOBJECT.toString())) {
                encodingProperty.setStyle(EncodingProperty.StyleEnum.DEEPOBJECT);
            } else if (value.equals(EncodingProperty.StyleEnum.PIPEDELIMITED.toString())) {
                encodingProperty.setStyle(EncodingProperty.StyleEnum.PIPEDELIMITED);
            } else if (value.equals(EncodingProperty.StyleEnum.SPACEDELIMITED.toString())) {
                encodingProperty.setStyle(EncodingProperty.StyleEnum.SPACEDELIMITED);
            } else {
                result.invalidType(location, "style", "string", node);
            }
        }

        Boolean explode = getBoolean("explode", node, false, location, result);
        encodingProperty.setExplode(explode);

        Boolean allowReserved = getBoolean("allowReserved", node, false, location, result);
        encodingProperty.setAllowReserved(allowReserved);

        ObjectNode headersObject = getObject("headers", node, false, location, result);
        if (headersObject!= null){
            encodingProperty.setHeaders(getHeaders(headersObject, location, result));
        }

        return encodingProperty;
    }

    public Map<String, Link> getLinks(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }
        Map<String, Link> links = new LinkedHashMap<>();

        Set<String> linkKeys = getKeys(obj);
        for(String linkName : linkKeys) {
            JsonNode linkValue = obj.get(linkName);
            if (!linkValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                result.invalidType(location, linkName, "object", linkValue);
            } else {
                ObjectNode link = (ObjectNode) linkValue;
                Link linkObj = getLink(link, location + ".'" + linkName + "'", result);
                if(linkObj !=null) {
                    links.put(linkName, linkObj);
                }
            }

        }
        return links;
    }

    public Link getLink(ObjectNode linkNode, String location, ParseResult result) {
        if (linkNode == null) {
            return null;
        }

        Link link = new Link();

        String value = getString("operationRef", linkNode, false, location, result);
        link.setOperationRef(value);

        value = getString("operationId", linkNode, false, location, result);
        link.setOperationId(value);

        ObjectNode parametersObject = getObject("parameters",linkNode,false,location,result);
        if (parametersObject!= null) {
            //TODO link.setParameters(getLinkParameters(parametersObject, location, result));
        }

        ObjectNode headerObject = getObject("headers",linkNode,false,location,result);
        if (headerObject!= null) {
            link.setHeaders(getHeaders(headerObject, location, result));
        }

        ObjectNode serverObject = getObject("server",linkNode,false,location,result);
        if (serverObject!= null) {
            //TODO ask ron link.setServer(getSchema(headerObject, location, result));
        }

        value = getString("description", linkNode, false, location, result);
        link.setDescription(value);

        return link;
    }

    public Map <String,Callback> getCallbacks(ObjectNode node, String location, ParseResult result){
        if (node == null) {
            return null;
        }
        Callbacks callbacks = new Callbacks();
        Set<String> keys = getKeys(node);
        for(String key : keys) {
            Callback callback = getCallback((ObjectNode) node.get(key), location, result);
            if (callback != null) {
                callbacks.addCallback(key, callback);
            }
        }
        return callbacks;
    }

    public Callback getCallback(ObjectNode node, String location, ParseResult result) {
        if (node == null) {
            return null;
        }

        Callback callback = new Callback();


        ObjectNode callbackObject = getObject("callback", node, false, location, result);
        if (callbackObject!= null){
            //callback.addPathItem(();
        }

        return callback;
    }


    public License getLicense(ObjectNode node, String location, ParseResult result) {
        if (node == null)
            return null;

        License license = new License();

        String value = getString("name", node, true, location, result);
        license.setName(value);

        value = getString("url", node, false, location, result);
        license.setUrl(value);

        // extra keys
        Set<String> keys = getKeys(node);
        for (String key : keys) {
            if (key.startsWith("x-")) {
                //license.setVendorExtension(key, extension(node.get(key)));
            } else if (!LICENSE_KEYS.contains(key)) {
                result.extra(location + ".license", key, node.get(key));
            }
        }

        return license;
    }

    public Contact getContact(ObjectNode node, String location, ParseResult result) {
        if (node == null)
            return null;

        Contact contact = new Contact();

        String value = getString("name", node, false, location + ".name", result);
        contact.setName(value);

        value = getString("url", node, false, location + ".url", result);
        contact.setUrl(value);

        value = getString("email", node, false, location + ".email", result);
        contact.setEmail(value);

        // extra keys
        Set<String> keys = getKeys(node);
        for (String key : keys) {
            if (!CONTACT_KEYS.contains(key)) {
                result.extra(location + ".contact", key, node.get(key));
            }
        }

        return contact;
    }

    public XML getXml(ObjectNode node, String location, ParseResult result){
        if (node == null) {
            return null;
        }
        XML xml = new XML();

        String value = getString("name", node, false, location + ".name", result);
        xml.setName(value);

        value = getString("namespace", node, false, location + ".url", result);
        xml.setNamespace(value);

        value = getString("prefix", node, false, location + ".email", result);
        xml.setPrefix(value);

        Boolean attribute = getBoolean("attribute", node, false, location, result);
        xml.setAttribute(attribute);

        Boolean wrapped = getBoolean("wrapped", node, false, location, result);
        xml.setWrapped(wrapped);

        return xml;

    }

    public ArrayNode getArray(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        JsonNode value = node.get(key);
        ArrayNode an = null;
        if (value == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else if (!value.getNodeType().equals(JsonNodeType.ARRAY)) {
            result.invalidType(location, key, "array", value);
        } else {
            an = (ArrayNode) value;
        }
        return an;
    }

    public Boolean getBoolean(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        Boolean value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        } else {
            if (v.getNodeType().equals(JsonNodeType.BOOLEAN)) {
                value = v.asBoolean();
            } else if (v.getNodeType().equals(JsonNodeType.STRING)) {
                String stringValue = v.textValue();
                return Boolean.parseBoolean(stringValue);
            }
        }
        return value;
    }

    public BigDecimal getBigDecimal(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        BigDecimal value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        }
        else if(v.getNodeType().equals(JsonNodeType.NUMBER)) {
            value = new BigDecimal(v.asText());
        }
        else if(!v.isValueNode()) {
            result.invalidType(location, key, "double", node);
        }
        return value;
    }



    public Integer getInteger(String key, ObjectNode node, boolean required, String location, ParseResult result) {
        Integer value = null;
        JsonNode v = node.get(key);
        if (node == null || v == null) {
            if (required) {
                result.missing(location, key);
                result.invalid();
            }
        }
        else if(v.getNodeType().equals(JsonNodeType.NUMBER)) {
            value = v.intValue();
        }
        else if(!v.isValueNode()) {
            result.invalidType(location, key, "integer", node);
        }
        return value;
    }


    public List<Parameter> getParameterList(ArrayNode obj, String location, ParseResult result) {
        List<Parameter> parameters = new ArrayList<>();
        if (obj == null) {
            return parameters;
        }
        for (JsonNode item : obj) {
            if (item.getNodeType().equals(JsonNodeType.OBJECT)) {
                Parameter parameter = getParameter((ObjectNode) item, location, result);
                if (parameter != null) {
                    parameters.add(parameter);
                }
            }
        }
        return parameters;
    }

    public Parameter getParameter(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }

        String value = getString("in", obj, true, location, result);

        if (StringUtils.isBlank(value)) {
            return null;
        }

        Parameter parameter = null;

        if (QUERY_PARAMETER.equals(value)) {
            parameter = new QueryParameter();
        } else if (HEADER_PARAMETER.equals(value)) {
            parameter = new HeaderParameter();
        } else if (PATH_PARAMETER.equals(value)) {
            parameter = new PathParameter();
        } else if (COOKIE_PARAMETER.equals(value)) {
            parameter = new CookieParameter();
        }

        if (parameter == null) {
            result.invalidType(location, "in", "string", obj);
            return null;
        }

        parameter.setIn(value);
        value = getString("name", obj, true, location, result);
        parameter.setName(value);

        value = getString("description", obj, false, location, result);
        parameter.setDescription(value);

        Boolean required = getBoolean("required", obj, false, location, result);
        if (required != null) {
            parameter.setRequired(required);
        }

        Boolean deprecated = getBoolean("deprecated", obj, false, location, result);
        if (deprecated != null) {
            parameter.setDeprecated(deprecated);
        }

        Boolean allowEmptyValue = getBoolean("allowEmptyValue", obj, false, location, result);
        if (allowEmptyValue != null) {
            parameter.setAllowEmptyValue(allowEmptyValue);
        }

        Boolean explode = getBoolean("explode", obj, false, location, result);
        if (explode != null) {
            parameter.setExplode(explode);
        } else {
            parameter.setExplode(Boolean.FALSE);
        }

        value = getString("style", obj, true, location, result);
        setStyle(value, parameter, location, obj, result);


        ObjectNode parameterObject = getObject("schema",obj,false,location,result);
        if (parameterObject!= null) {
            parameter.setSchema(getSchema(parameterObject,location,result));
        }

        //TODO: examples

        value = getString("example", obj, false, location, result);
        parameter.setExample(value);

        ObjectNode examplesNode = getObject("examples",obj,false,location,result);
        if(examplesNode!= null) {
            parameter.setExamples(getExamples(examplesNode, location, result));
        }

        ObjectNode contentNode = getObject("content",obj,false,location,result);
        if(contentNode!= null) {
            parameter.setContent(getContent(contentNode, location, result));
        }
        return parameter;
    }


    public Map<String, Header> getHeaders(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }
        Map<String, Header> headers = new LinkedHashMap<>();

        Set<String> headerKeys = getKeys(obj);
        for(String headerName : headerKeys) {
            JsonNode headerValue = obj.get(headerName);
            if(headerName.startsWith("x-")) {
                //result.unsupported(location, pathName, pathValue);
            }
            else {
                if (!headerValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                    result.invalidType(location, headerName, "object", headerValue);
                } else {
                    ObjectNode header = (ObjectNode) headerValue;
                    Header headerObj = getHeader(header, location + ".'" + headerName + "'", result);
                    headers.put(headerName, headerObj);
                }
            }
        }

        return headers;
    }

    public Header getHeader(ObjectNode headerNode, String location, ParseResult result) {
        if (headerNode == null) {
            return null;
        }

        Header header = new Header();

        String value = getString("description", headerNode, false, location, result);
        header.setDescription(value);

        Boolean required = getBoolean("required", headerNode, false, location, result);
        if (required != null) {
            header.setRequired(required);
        }

        Boolean deprecated = getBoolean("deprecated", headerNode, false, location, result);
        if (deprecated != null) {
            header.setDeprecated(deprecated);
        }

        Boolean allowEmptyValue = getBoolean("allowEmptyValue", headerNode, false, location, result);
        if (allowEmptyValue != null) {
            header.setAllowEmptyValue(allowEmptyValue);
        }

        Boolean explode = getBoolean("explode", headerNode, false, location, result);
        if (explode != null) {
            header.setExplode(explode);
        } else {
            header.setExplode(Boolean.FALSE);
        }

        header.setStyle(Header.StyleEnum.SIMPLE);

        ObjectNode headerObject = getObject("schema",headerNode,false,location,result);
        if (headerObject!= null) {
            header.setSchema(getSchema(headerObject, location, result));
        }
        //TODO: examples
        value = getString("example", headerNode, false, location, result);
        header.setExample(value);

        ObjectNode contentNode = getObject("content",headerNode,false,location,result);
        if (contentNode!= null){
            header.setContent(getContent(contentNode,location,result));
        }


        return header;
    }

    public Schema getSchema(ObjectNode node, String location, ParseResult result){
        if(node== null){
            return null;
        }
        Schema schema = new Schema();

            String value = getString("title",node,false,location,result);
            schema.setTitle(value);

            BigDecimal bigDecimal = getBigDecimal("multipleOf",node,false,location,result);
            schema.setMultipleOf(bigDecimal);

            bigDecimal = getBigDecimal("maximum", node, false, location, result);
            schema.setMaximum(bigDecimal);

            Boolean bool = getBoolean("exclusiveMaximum", node, false, location, result);
            schema.setExclusiveMaximum(bool);

            bigDecimal = getBigDecimal("minimum", node, false, location, result);
            schema.setMinimum(bigDecimal);

            bool = getBoolean("exclusiveMinimum", node, false, location, result);
            schema.setExclusiveMinimum(bool);

            Integer integer = getInteger("minLength", node, false, location, result);
            schema.setMinLength(integer);

            integer = getInteger("maxLength", node, false, location, result);
            schema.setMaxLength(integer);


            String pattern = getString("pattern", node, false, location, result);
            schema.setPattern(pattern);

            integer = getInteger("maxItems", node, false, location, result);
            schema.setMaxItems(integer);

            integer = getInteger("minItems", node, false, location, result);
            schema.setMinItems(integer);

            bool = getBoolean("uniqueItems", node, false, location, result);
            schema.setUniqueItems(bool);

            integer = getInteger("maxProperties", node, false, location, result);
            schema.setMaxProperties(integer);

            integer = getInteger("minProperties", node, false, location, result);
            schema.setMinProperties(integer);

            ArrayNode required = getArray("required", node, false, location, result);
            if(required != null) {
                List<String> requiredList = new ArrayList<>();
                for (JsonNode n : required) {
                    if(n.getNodeType().equals(JsonNodeType.STRING)) {
                        requiredList.add(((TextNode) n).textValue());
                    }
                    else {
                        result.invalidType(location, "required", "string", n);
                    }
                }
                if(requiredList.size() > 0) {
                    schema.setRequired(requiredList);
                }
            }

            ArrayNode an = getArray("enum", node, false, location, result);
            if(an != null) {
                List<String> _enum = new ArrayList<>();
                for(JsonNode n : an) {
                    if(n.isValueNode()) {
                        _enum.add(n.asText());
                    }
                    else {
                        result.invalidType(location, "enum", "value", n);
                    }
                }
                schema.setEnum(_enum);
            }

            value = getString("type",node,false,location,result);
            schema.setType(value);

            // MISSING SCHEMA OBJECTS allOf oneOf anyOf items

            String allOf = getString("allOf", node, false, location, result);
            if(allOf == null){
                AllOfSchema allOfSchema = new AllOfSchema();
            }

            //Schema not;
            /*ObjectNode notObj = getObject("not", node, false, location, result);
            Schema not = getSchema(notObj, location, result);
            schema.setNot(not);*/

            //Map <String, Schema> properties = null;
            /*ObjectNode propertiesObj = getObject("properties", node, false, location, result);
            Schema property = getSchema(propertiesObj, location, result);
            schema.setProperties(property);*/


            //Schema additionalProperties = null;

            value = getString("description",node,false,location,result);
            schema.setDescription(value);

            value = getString("format", node, false, location, result);
            schema.setFormat(value);


            ArrayNode array = getArray("default", node, false, location, result);
            schema.setDefault(array);

            //discriminator  xml

            bool = getBoolean("nullable", node, false, location, result);
            schema.setNullable(bool);

            bool = getBoolean("readOnly", node, false, location, result);
            schema.setReadOnly(bool);

            bool = getBoolean("writeOnly", node, false, location, result);
            schema.setWriteOnly(bool);



            ObjectNode xmlNode = getObject("xml", node, false, location, result);
            XML xml = getXml(xmlNode, location, result);
            schema.setXml(xml);


            ObjectNode externalDocs = getObject("externalDocs", node, false, location, result);
            ExternalDocumentation docs = getExternalDocs(externalDocs, location, result);
            schema.setExternalDocs(docs);

            value = getString("example",node,false,location,result);
            schema.setExample(value);

            bool = getBoolean("deprecated", node, false, location, result);
            schema.setDeprecated(bool);


        return schema;

    }



    public Map<String, Example> getExamples(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }
        Map<String, Example> examples = new LinkedHashMap<>();

        Set<String> exampleKeys = getKeys(obj);
        for(String exampleName : exampleKeys) {
            JsonNode exampleValue = obj.get(exampleName);
            if(exampleName.startsWith("x-")) {
                //result.unsupported(location, pathName, pathValue);
            }
            else {
                if (!exampleValue.getNodeType().equals(JsonNodeType.OBJECT)) {
                    result.invalidType(location, exampleName, "object", exampleValue);
                } else {
                    ObjectNode example = (ObjectNode) exampleValue;
                    Example exampleObj = getExample(example, location + ".'" + exampleName + "'", result);
                    examples.put(exampleName, exampleObj);
                }
            }
        }

        return examples;
    }

    public Example getExample(ObjectNode node, String location, ParseResult result) {
        if (node == null)
            return null;

        Example example = new Example();
        String value = getString("summary", node, false, location, result);
        example.setSummary(value);

        value = getString("description", node, false, location, result);
        example.setDescription(value);

        value = getString("value", node, false, location, result);
        example.setValue(value);

        value = getString("externalValue", node, false, location, result);
        example.setExternalValue(value);



        return example;
    }


    public void setStyle(String value, Parameter parameter, String location, ObjectNode obj, ParseResult result) {
        if (StringUtils.isBlank(value)) {
            if (QUERY_PARAMETER.equals(parameter.getIn()) || COOKIE_PARAMETER.equals(parameter.getIn())) {
                parameter.setStyle(Parameter.StyleEnum.FORM);
            } else if (PATH_PARAMETER.equals(parameter.getIn()) || HEADER_PARAMETER.equals(parameter.getIn())) {
                parameter.setStyle(Parameter.StyleEnum.SIMPLE);
            }
        } else {
            if (value.equals(Parameter.StyleEnum.FORM.toString())) {
                parameter.setStyle(Parameter.StyleEnum.FORM);
            } else if (value.equals(Parameter.StyleEnum.DEEPOBJECT.toString())) {
                parameter.setStyle(Parameter.StyleEnum.DEEPOBJECT);
            } else if (value.equals(Parameter.StyleEnum.LABEL.toString())) {
                parameter.setStyle(Parameter.StyleEnum.LABEL);
            } else if (value.equals(Parameter.StyleEnum.MATRIX.toString())) {
                parameter.setStyle(Parameter.StyleEnum.MATRIX);
            } else if (value.equals(Parameter.StyleEnum.PIPEDELIMITED.toString())) {
                parameter.setStyle(Parameter.StyleEnum.PIPEDELIMITED);
            } else if (value.equals(Parameter.StyleEnum.SIMPLE.toString())) {
                parameter.setStyle(Parameter.StyleEnum.SIMPLE);
            } else if (value.equals(Parameter.StyleEnum.SPACEDELIMITED.toString())) {
                parameter.setStyle(Parameter.StyleEnum.SPACEDELIMITED);
            } else {
                result.invalidType(location, "style", "string", obj);
            }
        }
    }

    public ApiResponses getResponses(ObjectNode node, String location, ParseResult result) {
        if (node == null) {
            return null;
        }

        ApiResponses apiResponses = new ApiResponses();
        Set<String> keys = getKeys(node);

        for (String key : keys) {

            if (key.startsWith("x-")) {
                // TODO: check the extension for this object.
            } else {
                ObjectNode obj = getObject(key, node, false, location + "responses", result);
                ApiResponse response = getResponse(obj, location + "." + key, result);
                apiResponses.put(key, response);
            }
        }

        return apiResponses;
    }

    public ApiResponse getResponse(ObjectNode node, String location, ParseResult result) {
        if (node == null) {
            return null;
        }

        ApiResponse apiResponse = new ApiResponse();
        JsonNode ref = node.get("$ref");
        if (ref != null) {
            if (ref.getNodeType().equals(JsonNodeType.STRING)) {
                //return refResponse((TextNode) ref, location, result);
            } else {
                result.invalidType(location, "$ref", "string", node);
                return null;
            }
        }

        String value = getString("description", node, true, location, result);
        apiResponse.description(value);


        ObjectNode headerObject = getObject("headers", node, true, location, result);
        if (headerObject != null) {
            Map<String,Header> headers = getHeaders(headerObject, location, result);
            apiResponse.setHeaders(headers);
        }
        
        ObjectNode linksObj = getObject("links", node, true, location, result);
        if (linksObj != null) {
            //TODO ask tony Link links = getLinks(linksObj, location, result);
            //TODO apiResponse.setLinks(links);
        }

        ObjectNode contentObject = getObject("content", node, true, location, result);
        if (contentObject != null) {
            apiResponse.setContent(getContent(contentObject, location, result));
        }

        // extra keys
        apiResponse.setExtensions(new LinkedHashMap<>());

        Set<String> keys = getKeys(node);
        for(String key : keys) {
            if(key.startsWith("x-")) {
                apiResponse.getExtensions().put(key, Json.mapper().convertValue(node.get(key), Object.class));
            } else if (!RESPONSE_KEYS.contains(key)) {
                result.extra(location, key, node.get(key));
            }
        }
        return apiResponse;
    }

    public List<String> getTagsStrings(ArrayNode nodes, String location, ParseResult result) {
        if (nodes == null)
            return null;

        List<String> tags = new ArrayList<>();

        for (JsonNode node : nodes) {
            if (node.getNodeType().equals(JsonNodeType.STRING)) {
                tags.add(node.textValue());
            }
        }
        return tags;
    }




    public Operation getOperation(ObjectNode obj, String location, ParseResult result) {
        if (obj == null) {
            return null;
        }
        Operation operation = new Operation();

        ArrayNode array = getArray("tags", obj, false, location, result);
        List<String> tags = getTagsStrings(array, location, result);
        if (tags != null) {
            operation.setTags(tags);
        }
        String value = getString("summary", obj, false, location, result);
        operation.setSummary(value);

        value = getString("description", obj, false, location, result);
        operation.setDescription(value);

        ObjectNode externalDocs = getObject("externalDocs", obj, false, location, result);
        ExternalDocumentation docs = getExternalDocs(externalDocs, location, result);
        operation.setExternalDocs(docs);

        value = getString("operationId", obj, false, location, result);
        operation.operationId(value);

        ArrayNode parameters = getArray("parameters", obj, false, location, result);
        operation.setParameters(getParameterList(parameters, location, result));

        final ObjectNode requestObjectNode = getObject("requestBody", obj, false, location, result);
        operation.setRequestBody(getRequestBody(requestObjectNode, location, result));

        ObjectNode responsesNode = getObject("responses", obj, false, location, result);
        ApiResponses responses = getResponses(responsesNode, "responses", result);
        operation.setResponses(responses);

        //TODO Callbacks

        Boolean deprecated = getBoolean("deprecated", obj, false, location, result);
        if (deprecated != null) {
            operation.setDeprecated(deprecated);
        }

        array = getArray("servers", obj, false, location, result);
        if (obj != null) {
            operation.setServers(getServersList(array, location, result));
        }


        array = getArray("security", obj, false, location, result);
        if (obj != null) {
            operation.setSecurity(getSecurityRequirementsList(array, "security", result));
        }



        // extra keys
        Set<String> keys = getKeys(obj);
        for (String key : keys) {
            if (key.startsWith("x-")) {
                //output.setVendorExtension(key, extension(obj.get(key)));
            } else if (!OPERATION_KEYS.contains(key)) {
                result.extra(location, key, obj.get(key));
            }
        }

        return operation;
    }

    public List<SecurityRequirement> getSecurityRequirementsList(ArrayNode nodes, String location, ParseResult result) {
        if (nodes == null)
            return null;

        List<SecurityRequirement> securityRequirements = new ArrayList<>();

        for (JsonNode node : nodes) {
            if (node.getNodeType().equals(JsonNodeType.OBJECT)) {
                SecurityRequirement securityRequirement = new SecurityRequirement();
                Set<String> keys = getKeys((ObjectNode) node);
                for (String key : keys) {
                    if (key != null) {
                        securityRequirement.addList(key,node.textValue());
                        securityRequirements.add(securityRequirement);
                    }
                }


            }
        }
        return securityRequirements;
    }


    protected RequestBody getRequestBody(ObjectNode node, String location, ParseResult result) {
        if (node == null){
            return null;
        }
        final RequestBody body = new RequestBody();

        final String description = getString("description", node, false, location, result);
        body.setDescription(description);
        final Boolean required = getBoolean("required", node, false, location, result);
        body.setRequired(required);

        final ObjectNode contentNode = getObject("content", node, false, location, result);
        if (contentNode != null) {
            body.setContent(getContent(contentNode, location, result));
        }

        return body;
    }


    protected static class ParseResult {
        private boolean valid = true;
        private Map<Location, JsonNode> extra = new LinkedHashMap<>();
        private Map<Location, JsonNode> unsupported = new LinkedHashMap<>();
        private Map<Location, String> invalidType = new LinkedHashMap<>();
        private List<Location> missing = new ArrayList<>();

        public ParseResult() {
        }

        public void extra(String location, String key, JsonNode value) {
            extra.put(new Location(location, key), value);
        }

        public void missing(String location, String key) {
            missing.add(new Location(location, key));
        }

        public void invalidType(String location, String key, String expectedType, JsonNode value) {
            invalidType.put(new Location(location, key), expectedType);
        }

        public void invalid() {
            this.valid = false;
        }

        public List<String> getMessages() {
            List<String> messages = new ArrayList<String>();
            for (Location l : extra.keySet()) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is unexpected";
                messages.add(message);
            }
            for (Location l : invalidType.keySet()) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is not of type `" + invalidType.get(l) + "`";
                messages.add(message);
            }
            for (Location l : missing) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is missing";
                messages.add(message);
            }
            for (Location l : unsupported.keySet()) {
                String location = l.location.equals("") ? "" : l.location + ".";
                String message = "attribute " + location + l.key + " is unsupported";
                messages.add(message);
            }
            return messages;
        }
    }


    protected static class Location {
        private String location;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Location)) return false;

            Location location1 = (Location) o;

            if (location != null ? !location.equals(location1.location) : location1.location != null) return false;
            return !(key != null ? !key.equals(location1.key) : location1.key != null);

        }

        @Override
        public int hashCode() {
            int result = location != null ? location.hashCode() : 0;
            result = 31 * result + (key != null ? key.hashCode() : 0);
            return result;
        }

        private String key;

        public Location(String location, String key) {
            this.location = location;
            this.key = key;
        }
    }

}