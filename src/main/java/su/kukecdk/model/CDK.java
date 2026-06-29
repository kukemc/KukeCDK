package su.kukecdk.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * CDK模型类，表示一个CDK及其属性
 */
public class CDK {
    private String id;
    private String name;
    private int quantity;
    private boolean isSingleUse;
    private String commands;
    private Date expirationDate;
    private String requiredPermission;
    private String requiredGroup;
    private Set<String> redeemedPlayers;

    /**
     * 创建一个新的CDK对象
     * 
     * @param id CDK的ID
     * @param name CDK的名称
     * @param quantity CDK的数量
     * @param isSingleUse 是否为一次性使用
     * @param commands 兑换时执行的命令
     * @param expirationDate 过期时间
     */
    public CDK(String id, String name, int quantity, boolean isSingleUse, String commands, Date expirationDate) {
        this(id, name, quantity, isSingleUse, commands, expirationDate, null, null);
    }

    /**
     * 创建一个新的CDK对象
     *
     * @param id CDK的ID
     * @param name CDK的名称
     * @param quantity CDK的数量
     * @param isSingleUse 是否为一次性使用
     * @param commands 兑换时执行的命令
     * @param expirationDate 过期时间
     * @param requiredPermission 兑换所需权限
     * @param requiredGroup 兑换所需权限组
     */
    public CDK(String id, String name, int quantity, boolean isSingleUse, String commands, Date expirationDate, String requiredPermission, String requiredGroup) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.isSingleUse = isSingleUse;
        this.commands = commands;
        this.expirationDate = expirationDate;
        this.requiredPermission = normalizeOptional(requiredPermission);
        this.requiredGroup = normalizeOptional(requiredGroup);
        this.redeemedPlayers = new HashSet<>();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 检查CDK是否已过期
     * 
     * @return 如果CDK已过期则返回true，否则返回false
     */
    public boolean isExpired() {
        return expirationDate != null && new Date().after(expirationDate);
    }

    /**
     * 检查玩家是否已经兑换过此CDK
     * 
     * @param playerName 玩家名称
     * @return 如果玩家已兑换过则返回true，否则返回false
     */
    public boolean hasPlayerRedeemed(String playerName) {
        return redeemedPlayers.contains(playerName);
    }

    /**
     * 添加已兑换玩家
     * 
     * @param playerName 玩家名称
     */
    public void addRedeemedPlayer(String playerName) {
        redeemedPlayers.add(playerName);
    }

    /**
     * 减少CDK数量
     */
    public void decreaseQuantity() {
        quantity--;
    }

    /**
     * 增加CDK数量
     * 
     * @param amount 增加的数量
     */
    public void increaseQuantity(int amount) {
        quantity += amount;
    }

    /**
     * 获取CDK的ID
     * 
     * @return CDK的ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取CDK的名称
     * 
     * @return CDK的名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取CDK的数量
     * 
     * @return CDK的数量
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * 检查CDK是否为一次性使用
     * 
     * @return 如果CDK为一次性使用则返回true，否则返回false
     */
    public boolean isSingleUse() {
        return isSingleUse;
    }

    /**
     * 获取CDK兑换时执行的命令
     * 
     * @return CDK兑换时执行的命令
     */
    public String getCommands() {
        return commands;
    }

    /**
     * 获取CDK的过期时间
     * 
     * @return CDK的过期时间
     */
    public Date getExpirationDate() {
        return expirationDate;
    }

    /**
     * 获取兑换所需权限
     *
     * @return 兑换所需权限
     */
    public String getRequiredPermission() {
        return requiredPermission;
    }

    /**
     * 设置兑换所需权限
     *
     * @param requiredPermission 兑换所需权限
     */
    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = normalizeOptional(requiredPermission);
    }

    /**
     * 获取兑换所需权限组
     *
     * @return 兑换所需权限组
     */
    public String getRequiredGroup() {
        return requiredGroup;
    }

    /**
     * 设置兑换所需权限组
     *
     * @param requiredGroup 兑换所需权限组
     */
    public void setRequiredGroup(String requiredGroup) {
        this.requiredGroup = normalizeOptional(requiredGroup);
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setCommands(String commands) {
        this.commands = commands;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * 检查CDK是否配置了额外使用条件
     *
     * @return 如果配置了权限或权限组限制则返回true
     */
    public boolean hasUseConditions() {
        return requiredPermission != null || requiredGroup != null;
    }

    /**
     * 获取已兑换玩家列表
     * 
     * @return 已兑换玩家列表
     */
    public Set<String> getRedeemedPlayers() {
        return redeemedPlayers;
    }

    /**
     * 设置已兑换玩家列表
     * 
     * @param redeemedPlayers 已兑换玩家列表
     */
    public void setRedeemedPlayers(Set<String> redeemedPlayers) {
        this.redeemedPlayers = redeemedPlayers;
    }

    @Override
    public String toString() {
        String expirationStr = expirationDate != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(expirationDate) : "永久有效";
        return "CDK ID: " + id + ", 名称: " + name + ", 剩余数量: " + quantity + ", 类型: " + (isSingleUse ? "一次性" : "多次使用") + ", 有效时间: " + expirationStr;
    }
}
