# Room Migration Validation

当前 Android 数据库版本为 6。

- 4 -> 5：补 questionText、userAnswer、correctAnswer、notesUpdatedAt、noteImagesUpdatedAt、reviewUpdatedAt。
- 5 -> 6：重建 questions 表，修复早期错误测试包可能留下的 nullable questionText、userAnswer、correctAnswer schema。

验证步骤：

1. 准备一个使用 version 4 或旧版错误 version 5 数据库的旧安装包或旧用户数据。
2. 安装新版应用，让 AppDatabase 正常打开。
3. 观察启动日志，确认没有出现 `Room migration didn't properly handle`、`Schema mismatch` 或 `IllegalStateException`。
4. 抽查旧题目数据：questionText、userAnswer、correctAnswer 应为 `""`，而不是 `null`。
5. 抽查 notesUpdatedAt、noteImagesUpdatedAt、reviewUpdatedAt：
   - notes 非空时，notesUpdatedAt 应回填为 updatedAt。
   - noteImageRefs 非空时，noteImagesUpdatedAt 应回填为 updatedAt。
   - reviewCount > 0 或 lastReviewedAt 非空时，reviewUpdatedAt 应回填为 COALESCE(lastReviewedAt, updatedAt)。
6. 如果设备上装过之前那个 version 5 测试包，升级到新版后也应能正常打开，不应因为 questionText、userAnswer、correctAnswer 的 nullability 不一致而崩溃。
7. 执行 `./gradlew.bat :app:compileDebugKotlin`，确认编译通过。

说明：

- MIGRATION_4_5 中 questionText、userAnswer、correctAnswer 保持 `TEXT NOT NULL DEFAULT ''`，不要改回 nullable。
- MIGRATION_5_6 的职责是兼容已经落地成错误 nullable schema 的旧测试安装，不影响 notesUpdatedAt、noteImagesUpdatedAt、reviewUpdatedAt 的既有迁移结果。
- 业务模型 Question 继续使用空字符串表示“未填写”，这样与 Room schema 校验一致。