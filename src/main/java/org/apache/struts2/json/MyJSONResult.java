package org.apache.struts2.json;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.StrutsConstants;
import org.apache.struts2.StrutsStatics;
import org.apache.struts2.json.annotations.ExcludeProperties;
import org.apache.struts2.json.annotations.IncludeProperties;
import org.apache.struts2.json.annotations.MaxLevel;
import org.apache.struts2.json.smd.SMDGenerator;
import org.glassfish.jersey.server.model.AnnotatedMethod;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.Result;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.util.ValueStack;
import com.opensymphony.xwork2.util.WildcardUtil;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;

import yingjianhua.vote.actions.resource.AbstractCRUDAction;

public class MyJSONResult implements Result {

    private static final long serialVersionUID = 8624350183189931165L;

    private static final Logger LOG = LoggerFactory.getLogger(JSONResult.class);

    private String defaultEncoding = "ISO-8859-1";
    private List<Pattern> includeProperties;
    private List<Pattern> excludeProperties;
    private String root = "beans";
    private boolean wrapWithComments;
    private boolean prefix;
    private boolean enableSMD = false;
    private boolean enableGZIP = false;
    private boolean ignoreHierarchy = true;
    private boolean ignoreInterfaces = true;
    private boolean enumAsBean = JSONWriter.ENUM_AS_BEAN_DEFAULT;
    private boolean noCache = false;
    private boolean excludeNullProperties = false;
    private int statusCode;
    private int errorCode;
    private String callbackParameter;
    private String contentType;
    private String wrapPrefix;
    private String wrapSuffix;

    public static void main(String[] args) {
    	StringBuilder stringBuilder = new StringBuilder();
    	stringBuilder.append("{pkey:1}");
    	stringBuilder.insert(0, "{}&& ");
        System.out.println(stringBuilder);
	}
    @Inject(StrutsConstants.STRUTS_I18N_ENCODING)
    public void setDefaultEncoding(String val) {
        this.defaultEncoding = val;
    }

    /**
     * Gets a list of regular expressions of properties to exclude from the JSON
     * output.
     *
     * @return A list of compiled regular expression patterns
     */
    public List<Pattern> getExcludePropertiesList() {
        return this.excludeProperties;
    }

    /**
     * Sets a comma-delimited list of regular expressions to match properties
     * that should be excluded from the JSON output.
     *
     * @param commaDelim A comma-delimited list of regular expressions
     */
    public void setExcludeProperties(String[] excludePatterns) {
        if (excludePatterns != null) {
            this.excludeProperties = new ArrayList<Pattern>(excludePatterns.length + 2);
            for (String pattern : excludePatterns) {
            	this.excludeProperties.add(Pattern.compile(pattern));
            }
        } else {
        	this.excludeProperties = new ArrayList<Pattern>(2);
        }
        this.excludeProperties.add(Pattern.compile(".*handler.*"));
        this.excludeProperties.add(Pattern.compile(".*hibernateLazyInitializer.*"));
    }

    /**
     * Sets a comma-delimited list of wildcard expressions to match properties
     * that should be excluded from the JSON output.
     * 
     * @param commaDelim A comma-delimited list of wildcard patterns
     */
    public void setExcludeWildcards(String commaDelim) {
        Set<String> excludePatterns = JSONUtil.asSet(commaDelim);
        if (excludePatterns != null) {
            this.excludeProperties = new ArrayList<Pattern>(excludePatterns.size());
            for (String pattern : excludePatterns) {
                this.excludeProperties.add(WildcardUtil.compileWildcardPattern(pattern));
            }
        }
    }

    /**
     * @return the includeProperties
     */
    public List<Pattern> getIncludePropertiesList() {
        return includeProperties;
    }

    /**
     * Sets a comma-delimited list of regular expressions to match properties
     * that should be included in the JSON output.
     *
     * @param commaDelim A comma-delimited list of regular expressions
     */
    public void setIncludeProperties(String[] commaDelim) {
        includeProperties = JSONUtil.processIncludePatterns(new HashSet<String>(Arrays.asList(commaDelim)), JSONUtil.REGEXP_PATTERN);
    }

    /**
     * Sets a comma-delimited list of wildcard expressions to match properties
     * that should be included in the JSON output.
     *
     * @param commaDelim A comma-delimited list of wildcard patterns
     */
    public void setIncludeWildcards(String commaDelim) {
        includeProperties = JSONUtil.processIncludePatterns(JSONUtil.asSet(commaDelim), JSONUtil.WILDCARD_PATTERN);
    }

    public void execute(ActionInvocation invocation) throws Exception {
    	System.out.println("myJsonResult start");
        ActionContext actionContext = invocation.getInvocationContext();
        System.out.println("myJsonResult end");
        HttpServletRequest request = (HttpServletRequest) actionContext.get(StrutsStatics.HTTP_REQUEST);
        HttpServletResponse response = (HttpServletResponse) actionContext.get(StrutsStatics.HTTP_RESPONSE);

        try {
            Object rootObject;
            rootObject = readRootObject(invocation);
            if(rootObject != null) {
	            Method method = invocation.getProxy().getAction().getClass().getMethod(invocation.getProxy().getMethod());
	            AnnotatedMethod am = new AnnotatedMethod(method);
	            if (am.isAnnotationPresent(ExcludeProperties.class)) {
	            	setExcludeProperties(method.getAnnotation(ExcludeProperties.class).value());
	            } else {
	            	setExcludeProperties(null);
	            }
	            if (am.isAnnotationPresent(IncludeProperties.class)) {
	            	setIncludeProperties(method.getAnnotation(IncludeProperties.class).value());
	            } else {
	            	final int maxLevel;
	            	if(am.isAnnotationPresent(MaxLevel.class)) {
	            		maxLevel = method.getAnnotation(MaxLevel.class).value();
	            	} else {
	            		if(this.root.equals(AbstractCRUDAction.BEAN)) {
	            			maxLevel = 2;
	            		} else if(this.root.equals(AbstractCRUDAction.BEANS)) {
	            			maxLevel = 3;
	            		} else if(this.root.equals(AbstractCRUDAction.PAGES)) {
	            			maxLevel = 3;
	            		} else if(rootObject.getClass().isArray() || rootObject instanceof Iterable) {
	            			maxLevel = 3;
	            		} else {
	            			maxLevel = 2;
	            		}
	            	}
	            	if(maxLevel > 0) {
	            		int i = 0;
	            		String[] maxLevelPattern = new String[maxLevel];
	            		StringBuilder sb = new StringBuilder("[a-zA-Z0-9_\\[\\]\\{\\}]+");
	            		while(++i<maxLevel){
	            			sb.append("\\.[a-zA-Z0-9_\\[\\]\\{\\}]+");
	            			maxLevelPattern[i] = sb.toString();
	            		}
	            		maxLevelPattern[0] = "[a-zA-Z0-9_\\[\\]\\{\\}]+";
	            		setIncludeProperties(maxLevelPattern);
	            	}
	            }
            }
            if(rootObject instanceof JSONObject) {
            	writeToResponse(response, ((JSONObject)rootObject).toJSONString(), enableGzip(request));
            } else if(rootObject instanceof JSONArray) {
            	writeToResponse(response, ((JSONArray)rootObject).toJSONString(), enableGzip(request));
            } else if(this.root.equals(AbstractCRUDAction.BEAN)) {
            	String jsonString = createJSONString(request, rootObject);
            	JSONObject jsonObject = JSONObject.parseObject(jsonString);
            	if(jsonObject == null) jsonObject = new JSONObject();
        		jsonObject.put("success", true);
        		writeToResponse(response, jsonObject.toJSONString(), enableGzip(request));
            } else {
            	writeToResponse(response, createJSONString(request, rootObject), enableGzip(request));
            }
        } catch (IOException exception) {
            LOG.error(exception.getMessage(), exception);
            throw exception;
        }
    }

    private Object readRootObject(ActionInvocation invocation) {
        if (enableSMD) {
            return buildSMDObject(invocation);
        }
        return findRootObject(invocation);
    }

    private Object findRootObject(ActionInvocation invocation) {
        Object rootObject;
        if (this.root != null) {
            ValueStack stack = invocation.getStack();
            rootObject = stack.findValue(root);
        } else {
            rootObject = invocation.getStack().peek(); // model overrides action
        }
        return rootObject;
    }

    private String createJSONString(HttpServletRequest request, Object rootObject) throws JSONException {
        String json;
        json = JSONUtil.serialize(rootObject, excludeProperties, includeProperties, ignoreHierarchy,
                enumAsBean, excludeNullProperties);
        json = addCallbackIfApplicable(request, json);
        return json;
    }

    private boolean enableGzip(HttpServletRequest request) {
        return enableGZIP && JSONUtil.isGzipInRequest(request);
    }

    protected void writeToResponse(HttpServletResponse response, String json, boolean gzip) throws IOException {
        JSONUtil.writeJSONToResponse(new SerializationParams(response, getEncoding(), isWrapWithComments(),
                json, false, gzip, noCache, statusCode, errorCode, prefix, contentType, wrapPrefix,
                wrapSuffix));
    }

    @SuppressWarnings("unchecked")
    protected org.apache.struts2.json.smd.SMD buildSMDObject(ActionInvocation invocation) {
        return new SMDGenerator(findRootObject(invocation), excludeProperties, ignoreInterfaces).generate(invocation);
    }

    /**
     * Retrieve the encoding <p/>
     *
     * @return The encoding associated with this template (defaults to the value
     *         of 'struts.i18n.encoding' property)
     */
    protected String getEncoding() {
        String encoding = this.defaultEncoding;

        if (encoding == null) {
            encoding = System.getProperty("file.encoding");
        }

        if (encoding == null) {
            encoding = "UTF-8";
        }

        return encoding;
    }

    protected String addCallbackIfApplicable(HttpServletRequest request, String json) {
        if ((callbackParameter != null) && (callbackParameter.length() > 0)) {
            String callbackName = request.getParameter(callbackParameter);
            if ((callbackName != null) && (callbackName.length() > 0))
                json = callbackName + "(" + json + ")";
        }
        return json;
    }

    /**
     * @return OGNL expression of root object to be serialized
     */
    public String getRoot() {
        return this.root;
    }

    /**
     * Sets the root object to be serialized, defaults to the Action
     *
     * @param root OGNL expression of root object to be serialized
     */
    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * @return Generated JSON must be enclosed in comments
     */
    public boolean isWrapWithComments() {
        return this.wrapWithComments;
    }

    /**
     * Wrap generated JSON with comments
     *
     * @param wrapWithComments
     */
    public void setWrapWithComments(boolean wrapWithComments) {
        this.wrapWithComments = wrapWithComments;
    }

    /**
     * @return Result has SMD generation enabled
     */
    public boolean isEnableSMD() {
        return this.enableSMD;
    }

    /**
     * Enable SMD generation for action, which can be used for JSON-RPC
     *
     * @param enableSMD
     */
    public void setEnableSMD(boolean enableSMD) {
        this.enableSMD = enableSMD;
    }

    public void setIgnoreHierarchy(boolean ignoreHierarchy) {
        this.ignoreHierarchy = ignoreHierarchy;
    }

    /**
     * Controls whether interfaces should be inspected for method annotations
     * You may need to set to this true if your action is a proxy as annotations
     * on methods are not inherited
     */
    public void setIgnoreInterfaces(boolean ignoreInterfaces) {
        this.ignoreInterfaces = ignoreInterfaces;
    }

    /**
     * Controls how Enum's are serialized : If true, an Enum is serialized as a
     * name=value pair (name=name()) (default) If false, an Enum is serialized
     * as a bean with a special property _name=name()
     *
     * @param enumAsBean
     */
    public void setEnumAsBean(boolean enumAsBean) {
        this.enumAsBean = enumAsBean;
    }

    public boolean isEnumAsBean() {
        return enumAsBean;
    }

    public boolean isEnableGZIP() {
        return enableGZIP;
    }

    public void setEnableGZIP(boolean enableGZIP) {
        this.enableGZIP = enableGZIP;
    }

    public boolean isNoCache() {
        return noCache;
    }

    /**
     * Add headers to response to prevent the browser from caching the response
     *
     * @param noCache
     */
    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    public boolean isIgnoreHierarchy() {
        return ignoreHierarchy;
    }

    public boolean isExcludeNullProperties() {
        return excludeNullProperties;
    }

    /**
     * Do not serialize properties with a null value
     *
     * @param excludeNullProperties
     */
    public void setExcludeNullProperties(boolean excludeNullProperties) {
        this.excludeNullProperties = excludeNullProperties;
    }

    /**
     * Status code to be set in the response
     *
     * @param statusCode
     */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Error code to be set in the response
     *
     * @param errorCode
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setCallbackParameter(String callbackParameter) {
        this.callbackParameter = callbackParameter;
    }

    public String getCallbackParameter() {
        return callbackParameter;
    }

    /**
     * Prefix JSON with "{} &&"
     *
     * @param prefix
     */
    public void setPrefix(boolean prefix) {
        this.prefix = prefix;
    }

    /**
     * Content type to be set in the response
     *
     * @param contentType
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getWrapPrefix() {
        return wrapPrefix;
    }

    /**
     * Text to be inserted at the begining of the response
     */
    public void setWrapPrefix(String wrapPrefix) {
        this.wrapPrefix = wrapPrefix;
    }

    public String getWrapSuffix() {
        return wrapSuffix;
    }

    /**
     * Text to be inserted at the end of the response
     */
    public void setWrapSuffix(String wrapSuffix) {
        this.wrapSuffix = wrapSuffix;
    }	
}
