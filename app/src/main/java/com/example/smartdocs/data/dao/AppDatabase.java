package com.example.smartdocs.data.dao;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.smartdocs.data.model.QaHistory;
import com.example.smartdocs.data.model.SavedFile;
import com.example.smartdocs.data.model.ScanDocument;

/*
     Singleton Room Database của ứng dụng.
- version 2: thêm cột is_favorite và page_count
- version 3: thêm bảng qa_history, saved_files; thêm cột tags, language, updated_at;
             thêm index trên scan_documents.created_at
 */
@Database(entities = {ScanDocument.class, QaHistory.class, SavedFile.class},
        version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "smart_docs.db";

    // Singleton instance — volatile đảm bảo thread-safe
    private static volatile AppDatabase instance;

    // Room tự triển khai các method abstract này
    public abstract DocumentDao documentDao();
    public abstract QaHistoryDao qaHistoryDao();
    public abstract SavedFileDao savedFileDao();

    // Migration từ version 1 → 2: thêm cột is_favorite và page_count
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Thêm cột is_favorite (INTEGER, mặc định 0 = không yêu thích)
            database.execSQL("ALTER TABLE scan_documents ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0");
            // Thêm cột page_count (INTEGER, mặc định 1)
            database.execSQL("ALTER TABLE scan_documents ADD COLUMN page_count INTEGER NOT NULL DEFAULT 1");
        }
    };

    // Migration từ version 2 → 3:
    // - Thêm cột tags, language, updated_at vào scan_documents
    // - Tạo index trên scan_documents.created_at để tăng tốc ORDER BY
    // - Tạo bảng qa_history để lưu lịch sử hỏi đáp AI
    // - Tạo bảng saved_files để lưu file yêu thích (thay thế SharedPreferences)
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Thêm các cột mới vào scan_documents
            database.execSQL("ALTER TABLE scan_documents ADD COLUMN tags TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE scan_documents ADD COLUMN language TEXT NOT NULL DEFAULT 'auto'");
            database.execSQL("ALTER TABLE scan_documents ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0");

            // Index tăng tốc query ORDER BY created_at DESC
            database.execSQL("CREATE INDEX IF NOT EXISTS index_scan_documents_created_at ON scan_documents(created_at)");

            // Tạo bảng lưu lịch sử Q&A — xóa tài liệu thì xóa Q&A theo (CASCADE)
            database.execSQL("CREATE TABLE IF NOT EXISTS qa_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "document_id INTEGER NOT NULL, "
                    + "question TEXT NOT NULL, "
                    + "answer TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "FOREIGN KEY(document_id) REFERENCES scan_documents(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_qa_history_document_id ON qa_history(document_id)");

            // Tạo bảng lưu file yêu thích — unique trên file_path
            database.execSQL("CREATE TABLE IF NOT EXISTS saved_files ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "file_path TEXT NOT NULL, "
                    + "file_name TEXT, "
                    + "file_type TEXT, "
                    + "added_at INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_saved_files_file_path ON saved_files(file_path)");
        }
    };

    /**
    Trả về singleton instance của AppDatabase.
    Dùng Double-Checked Locking để an toàn khi gọi từ nhiều thread.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    )
                    // Thêm đầy đủ các migration — không xóa dữ liệu người dùng
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build();
                }
            }
        }
        return instance;
    }
}
