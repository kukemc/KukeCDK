package su.kukecdk.api;

public class ApiResponse {
    private final boolean success;
    private final Object data;
    private final ApiError error;
    private final String requestId;

    private ApiResponse(boolean success, Object data, ApiError error, String requestId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.requestId = requestId;
    }

    public static ApiResponse success(Object data, String requestId) {
        return new ApiResponse(true, data, null, requestId);
    }

    public static ApiResponse error(String code, String message, String requestId) {
        return new ApiResponse(false, null, new ApiError(code, message), requestId);
    }

    private static class ApiError {
        private final String code;
        private final String message;

        private ApiError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
