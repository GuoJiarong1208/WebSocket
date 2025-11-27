import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 用户服务类 - 负责用户注册、登录的业务逻辑
 */
public class UserService {
    // 使用ConcurrentHashMap存储用户，线程安全
    // Key: 用户名, Value: 密码
    private final Map<String, String> users = new ConcurrentHashMap<>();

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @return 注册结果信息
     */
    public String register(String username, String password) {
        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            return "用户名不能为空";
        }
        if (password == null || password.trim().isEmpty()) {
            return "密码不能为空";
        }

        // 检查用户是否已存在
        if (users.containsKey(username)) {
            return "用户名已存在";
        }

        // 注册成功
        users.put(username, password);
        return "注册成功";
    }

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果信息
     */
    public String login(String username, String password) {
        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            return "用户名不能为空";
        }
        if (password == null || password.trim().isEmpty()) {
            return "密码不能为空";
        }

        // 检查用户是否存在
        if (!users.containsKey(username)) {
            return "用户不存在";
        }

        // 验证密码
        String storedPassword = users.get(username);
        if (storedPassword. equals(password)) {
            return "登录成功";
        } else {
            return "密码错误";
        }
    }

    /**
     * 获取当前用户数量（可选，用于调试）
     */
    public int getUserCount() {
        return users. size();
    }
}