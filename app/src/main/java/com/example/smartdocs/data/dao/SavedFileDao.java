package com.example.smartdocs.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.smartdocs.data.model.SavedFile;

import java.util.List;
@Dao
public interface SavedFileDao {

    // Thêm file yêu thích
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(SavedFile savedFile);

    // Xóa yêu thích theo đường dẫn tuyệt đối
    @Query("DELETE FROM saved_files WHERE file_path = :path")
    void deleteByPath(String path);

    // Lấy tất cả đường dẫn file yêu thích
    @Query("SELECT file_path FROM saved_files")
    List<String> getAllFavoritePaths();

    // Cập nhật đường dẫn và tên file khi người dùng đổi tên file
    @Query("UPDATE saved_files SET file_path = :newPath, file_name = :newName WHERE file_path = :oldPath")
    void updatePath(String oldPath, String newPath, String newName);
}
