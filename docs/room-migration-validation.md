# Room Migration Validation

当前 Android 数据库版本为 5，questions 表中的 questionText、userAnswer、correctAnswer 与 Entity 保持非空 String，对应迁移 SQL 为 TEXT NOT NULL DEFAULT ''。

验证步骤：

1. 准备一个使用 version 4 数据库的旧安装包或旧用户数据。
2. 安装新版应用，让 AppDatabase 正常打开。
3. 观察启动日志，确认没有出现 `Room migration didn't properly handle`、`Schema mismatch` 或 `IllegalStateException`。
4. 抽查旧题目数据：questionText、userAnswer、correctAnswer 应为 `""`，而不是 `null`。
5. 抽查 notesUpdatedAt、noteImagesUpdatedAt、reviewUpdatedAt：
   - notes 非空时，notesUpdatedAt 应回填为 updatedAt。
   - noteImageRefs 非空时，noteImagesUpdatedAt 应回填为 updatedAt。
   - reviewCount > 0 或 lastReviewedAt 非空时，reviewUpdatedAt 应回填为 COALESCE(lastReviewedAt, updatedAt)。
6. 执行 `./gradlew.bat :app:compileDebugKotlin`，确认编译通过。

说明：

- MIGRATION_4_5 中 questionText、userAnswer、correctAnswer 保持 `TEXT NOT NULL DEFAULT ''`，不要改回 nullable。
- 业务模型 Question 继续使用空字符串表示“未填写”，这样与 Room schema 校验一致。