package su.kukecdk;

import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.util.HashSet;
import java.util.Set;

public class CDK {
    public String id;
    public String name;
    public int quantity;
    public boolean isSingleUse;
    public String commands;
    public Date expirationDate;
    public Set<String> redeemedPlayers; // 新增字段

    public CDK(String id, String name, int quantity, boolean isSingleUse, String commands, Date expirationDate) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.isSingleUse = isSingleUse;
        this.commands = commands;
        this.expirationDate = expirationDate;
        this.redeemedPlayers = new HashSet<>(); // 初始化已兑换玩家列表
    }

    @Override
    public String toString() {
        String expirationStr = expirationDate != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(expirationDate) : "永久有效";
        return "CDK ID: " + id + ", 名称: " + name + ", 剩余数量: " + quantity + ", 类型: " + (isSingleUse ? "一次性" : "多次使用") + ", 有效时间: " + expirationStr;
    }
}
