package ff.pro.aichatali.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class RequestContext {

    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new ThreadLocal<>();
    private HttpServletRequest request;
    /**
     * 回滚操作 同步执行
     */
    private final List<Runnable> rollback = new ArrayList<>();
    @Setter
    private UserSimpleDto userInfo;
    private final List<Runnable> thirdWork = new ArrayList<>();
    @Setter
    private long deviceLibraryId = 0;
    @Setter
    int toolFlag;



    public void addThirdWork(Runnable runnable) {
        thirdWork.add(runnable);
    }

    public void addRollback(Runnable runnable) {
        this.rollback.add(runnable);
    }

    public Optional<Runnable> reduceRollback() {

        return this.rollback.stream().reduce((runnable1, runnable2) -> () -> {
            runnable1.run();
            runnable2.run();
        });
    }

    public Runnable getThirdWork() {
        return thirdWork.stream().reduce(() -> {
        }, (a, b) -> () -> {
            a.run();
            b.run();
        });
    }

    public void clearRollback() {
        this.rollback.clear();
    }

    public static RequestContext getRequestContext() {
        RequestContext requestContext = REQUEST_CONTEXT.get();
        if (requestContext == null) {
            REQUEST_CONTEXT.set(new RequestContext());
            requestContext = REQUEST_CONTEXT.get();
        }
        return requestContext;
    }

    // 添加一个方法用于创建当前上下文的副本
    public static RequestContext cloneRequestContext() {
        RequestContext currentContext = getRequestContext();
        RequestContext newContext = new RequestContext();

        // 复制所有必要属性
        newContext.request = currentContext.request;
        newContext.userInfo = currentContext.userInfo;
        newContext.deviceLibraryId = currentContext.deviceLibraryId;
        newContext.toolFlag = currentContext.toolFlag;

        // 注意：rollback 和 thirdWork 不应该被复制，因为它们是线程本地的执行任务

        return newContext;
    }

    // 添加一个方法用于设置当前上下文的副本
    public static void setClone(RequestContext newContext) {
        RequestContext requestContext = REQUEST_CONTEXT.get();
        if (requestContext != null) {
            throw new IllegalStateException("RequestContext 上下文冲突, 内存泄漏警告！");
        }
        REQUEST_CONTEXT.set(newContext);
        REQUEST_CONTEXT.get().setDeviceLibraryId(newContext.getDeviceLibraryId());
        REQUEST_CONTEXT.get().setRequest(newContext.request);
        REQUEST_CONTEXT.get().setUserInfo(newContext.userInfo);


    }

    public long getDeviceLibraryId() {
        if (deviceLibraryId != 0) {
            return deviceLibraryId;
        }
        if (request!=null) {
            String deviceLibraryIdStr = request.getHeader("X-Tenant-Id");
            if (deviceLibraryIdStr == null) {
                deviceLibraryIdStr = request.getParameter("tenantId");
            }
            if (deviceLibraryIdStr != null && !deviceLibraryIdStr.isEmpty()) {
                try {
                    return Long.parseLong(deviceLibraryIdStr);
                } catch (NumberFormatException e) {
                    // 忽略无效的deviceLibraryId
                }
            }
        }
        return "admin".equals(getUserInfo().getUsername()) ? -1L : 0L;
    }

    public static void removeRequestContext() {
        REQUEST_CONTEXT.remove();
    }

    public Optional<HttpServletRequest> getRequest() {
        return Optional.ofNullable(this.request);
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
        this.deviceLibraryId = getDeviceLibraryId();
    }

    public String getToken() {
        String token = this.request.getHeader("X-Token");
        if (StringUtils.isEmpty(token)) {
            token = request.getParameter("token");
        }
        return token;
    }

    public int getToolFlag() {
        if (this.request == null) return this.toolFlag;
        String toolFlag = this.request.getHeader("X-Tool-Flag");
        if (StringUtils.isBlank(toolFlag)) {
            return 0;
        }
        return Integer.parseInt(toolFlag);
    }


    public String getIp() {
        String ip = null;
        if (this.getRequest().isPresent()) {
            ip = this.request.getHeader("X-SubnetIP");
            if (StringUtils.isBlank(ip)) {
                ip = this.request.getHeader("X-Real-IP");
            }
            if (StringUtils.isBlank(ip)) {
                ip = this.request.getRemoteAddr();
            }
        }
        return ip;
    }

    public String getHost() {
        String userHost = "";
        if (this.getRequest().isPresent()) {
            userHost = this.request.getHeader("Host");
            if (StringUtils.isBlank(userHost)) {
                userHost = this.request.getRemoteHost();
            }
        }
        return userHost;
    }


    public UserSimpleDto getUserInfo() {
        return Optional.ofNullable(this.userInfo).orElse(new UserSimpleDto(0));
    }


    public boolean isHttps() {
        if (request == null) {
            return false;
        }
        return request.getHeader("x-request-protocol") != null && request.getHeader("x-request-protocol").equals("https");
    }
}