package com.example.smartdocs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.smartdocs.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
/*
  MainActivity — Host Activity duy nhất quản lý Bottom Navigation và các Fragment.
  Áp dụng kiến trúc Single Activity + show/hide Fragment:
  - Bottom Navigation đứng yên cố định ở đáy, không bao giờ bị nháy/chớp.
  - Giữ nguyên trạng thái (state preservation) của từng tab khi chuyển đổi.
 */
public class MainActivity extends AppCompatActivity {

    // Views
    FrameLayout fragmentContainer;
    BottomNavigationView bottomNav;

    // 4 Fragment tương ứng 4 tab
    HomeFragment homeFragment;
    FilesFragment filesFragment;
    HistoryFragment historyFragment;
    SettingsFragment settingsFragment;

    // Fragment đang hiển thị hiện tại
    Fragment activeFragment;
    String activeTag = "HOME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Ánh xạ views
        anhXaView();

        // 2. Khởi tạo các Fragment
        khoiTaoFragments(savedInstanceState);

        // 3. Thiết lập Bottom Navigation
        thietLapBottomNav();

        // 4. Khôi phục tab nếu Activity được tạo lại (sau khi đổi giao diện hoặc xoay màn hình)
        if (savedInstanceState != null) {
            int selectedTabId = savedInstanceState.getInt("selected_tab_id", R.id.nav_home);
            chonTab(selectedTabId);
        }

        // 5. Xử lý Intent nếu có yêu cầu mở tab cụ thể
        xuLyIntent(getIntent());

        // 6. Thiết lập xử lý nút Back (OnBackPressedDispatcher chuẩn AndroidX)
        thietLapXuLyBack();
    }

    void anhXaView() {
        fragmentContainer = (FrameLayout) findViewById(R.id.fragment_container);
        bottomNav = (BottomNavigationView) findViewById(R.id.bottomNav);
    }

    void khoiTaoFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            // Lần đầu mở Activity: tạo mới các Fragment
            homeFragment = new HomeFragment();
            filesFragment = new FilesFragment();
            historyFragment = new HistoryFragment();
            settingsFragment = new SettingsFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, homeFragment, "HOME")
                    .commit();

            activeFragment = homeFragment;
            activeTag = "HOME";
        } else {
            // Khi Activity recreate: phục hồi lại instance đã lưu trong FragmentManager
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("HOME");
            filesFragment = (FilesFragment) getSupportFragmentManager().findFragmentByTag("FILES");
            historyFragment = (HistoryFragment) getSupportFragmentManager().findFragmentByTag("HISTORY");
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("SETTINGS");

            if (homeFragment == null) homeFragment = new HomeFragment();
            if (filesFragment == null) filesFragment = new FilesFragment();
            if (historyFragment == null) historyFragment = new HistoryFragment();
            if (settingsFragment == null) settingsFragment = new SettingsFragment();

            activeTag = savedInstanceState.getString("active_tag", "HOME");
            if ("FILES".equals(activeTag)) {
                activeFragment = filesFragment;
            } else if ("HISTORY".equals(activeTag)) {
                activeFragment = historyFragment;
            } else if ("SETTINGS".equals(activeTag)) {
                activeFragment = settingsFragment;
            } else {
                activeFragment = homeFragment;
            }

            // Đảm bảo ẩn các fragment khác và chỉ hiển thị activeFragment
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            if (homeFragment.isAdded() && homeFragment != activeFragment) ft.hide(homeFragment);
            if (filesFragment.isAdded() && filesFragment != activeFragment) ft.hide(filesFragment);
            if (historyFragment.isAdded() && historyFragment != activeFragment) ft.hide(historyFragment);
            if (settingsFragment.isAdded() && settingsFragment != activeFragment) ft.hide(settingsFragment);
            if (activeFragment != null && activeFragment.isAdded()) {
                ft.show(activeFragment);
            }
            ft.commit();
        }
    }

    void thietLapBottomNav() {
        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    chuyenFragment(homeFragment, "HOME");
                    return true;
                } else if (id == R.id.nav_files) {
                    chuyenFragment(filesFragment, "FILES");
                    return true;
                } else if (id == R.id.nav_history) {
                    chuyenFragment(historyFragment, "HISTORY");
                    return true;
                } else if (id == R.id.nav_settings) {
                    chuyenFragment(settingsFragment, "SETTINGS");
                    return true;
                }
                return false;
            }
        });
    }

    /*
      Chuyển đổi tab mượt mà bằng cơ chế show / hide.
      Không hủy hay khởi tạo lại Fragment đã mở, giữ nguyên 100% dữ liệu và vị trí cuộn.
     */
    void chuyenFragment(Fragment fragmentMoi, String tag) {
        if (fragmentMoi == activeFragment || fragmentMoi == null) {
            return;
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        if (activeFragment != null) {
            ft.hide(activeFragment);
        }

        if (!fragmentMoi.isAdded()) {
            ft.add(R.id.fragment_container, fragmentMoi, tag);
        } else {
            ft.show(fragmentMoi);
        }

        ft.commit();
        activeFragment = fragmentMoi;
        activeTag = tag;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("active_tag", activeTag);
        if (bottomNav != null) {
            outState.putInt("selected_tab_id", bottomNav.getSelectedItemId());
        }
    }

    /*
      Chuyển tab từ code bên ngoài hoặc từ các Fragment con.
     */
    public void chonTab(int tabId) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(tabId);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        xuLyIntent(intent);
    }

    void xuLyIntent(Intent intent) {
        if (intent != null && intent.hasExtra("tab_id")) {
            int tabId = intent.getIntExtra("tab_id", R.id.nav_home);
            chonTab(tabId);
        }
    }

    /*
      Xử lý nút Back bằng OnBackPressedDispatcher.
     */
    void thietLapXuLyBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Nếu tab Files đang bật multi-select thì thoát chế độ chọn nhiều trước
                if (activeFragment == filesFragment && filesFragment != null && filesFragment.xuLyBack()) {
                    return;
                }

                // Nếu đang ở tab phụ (Files, History, Settings) -> bấm Back chuyển về Home
                if (activeFragment != homeFragment) {
                    chonTab(R.id.nav_home);
                    return;
                }

                // Đang ở Home tab -> đóng ứng dụng
                finish();
            }
        });
    }
}
