package com.example.smartdocs.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.smartdocs.R;
import com.example.smartdocs.data.QLTaiKhoan;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationBarView;

/*
  MainActivity — Host Activity duy nhat quan ly Bottom Navigation, Navigation Drawer va cac Fragment.
 */
public class MainActivity extends AppCompatActivity {

    // Views
    DrawerLayout drawerLayout;
    FrameLayout fragmentContainer;
    BottomNavigationView bottomNav;

    // Navigation Drawer views
    LinearLayout layoutDrawerUser;
    MaterialCardView cardDrawerAvatar;
    TextView txtDrawerAvatarChar, txtDrawerTen, txtDrawerGoiBadge, txtDrawerEmail;
    MaterialCardView cardDrawerUpgrade;

    LinearLayout layoutDrawerHome, layoutDrawerFiles, layoutDrawerHistory, layoutDrawerFavorites;
    ImageView imgNavHome, imgNavFiles, imgNavHistory, imgNavFavorites;
    TextView txtNavHome, txtNavFiles, txtNavHistory, txtNavFavorites;

    LinearLayout layoutDrawerOffline, layoutDrawerAi, layoutDrawerShare, layoutDrawerLogout;

    // 4 Fragment tuong ung 4 tab
    HomeFragment homeFragment;
    FilesFragment filesFragment;
    HistoryFragment historyFragment;
    SettingsFragment settingsFragment;

    // Fragment dang hien thi hien tai
    Fragment activeFragment;
    String activeTag = "HOME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Anh xa views
        anhXaView();

        // 2. Khoi tao Navigation Drawer
        thietLapDrawer();

        // 3. Khoi tao cac Fragment
        khoiTaoFragments(savedInstanceState);

        // 4. Thiet lap Bottom Navigation
        thietLapBottomNav();

        // 5. Khoi phuc tab neu Activity duoc tao lai
        if (savedInstanceState != null) {
            int selectedTabId = savedInstanceState.getInt("selected_tab_id", R.id.nav_home);
            chonTab(selectedTabId);
        }

        // 6. Xu ly Intent neu co yeu cau mo tab cu the
        xuLyIntent(getIntent());

        // 7. Thiet lap xu ly nut Back
        thietLapXuLyBack();
    }

    void anhXaView() {
        drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        fragmentContainer = (FrameLayout) findViewById(R.id.fragment_container);
        bottomNav = (BottomNavigationView) findViewById(R.id.bottomNav);

        // Drawer Profile & Upgrade
        layoutDrawerUser = (LinearLayout) findViewById(R.id.layoutDrawerUser);
        cardDrawerAvatar = (MaterialCardView) findViewById(R.id.cardDrawerAvatar);
        txtDrawerAvatarChar = (TextView) findViewById(R.id.txtDrawerAvatarChar);
        txtDrawerTen = (TextView) findViewById(R.id.txtDrawerTen);
        txtDrawerGoiBadge = (TextView) findViewById(R.id.txtDrawerGoiBadge);
        txtDrawerEmail = (TextView) findViewById(R.id.txtDrawerEmail);
        cardDrawerUpgrade = (MaterialCardView) findViewById(R.id.cardDrawerUpgrade);

        // Drawer Nav Items
        layoutDrawerHome = (LinearLayout) findViewById(R.id.layoutDrawerHome);
        layoutDrawerFiles = (LinearLayout) findViewById(R.id.layoutDrawerFiles);
        layoutDrawerHistory = (LinearLayout) findViewById(R.id.layoutDrawerHistory);
        layoutDrawerFavorites = (LinearLayout) findViewById(R.id.layoutDrawerFavorites);

        imgNavHome = (ImageView) findViewById(R.id.imgNavHome);
        imgNavFiles = (ImageView) findViewById(R.id.imgNavFiles);
        imgNavHistory = (ImageView) findViewById(R.id.imgNavHistory);
        imgNavFavorites = (ImageView) findViewById(R.id.imgNavFavorites);

        txtNavHome = (TextView) findViewById(R.id.txtNavHome);
        txtNavFiles = (TextView) findViewById(R.id.txtNavFiles);
        txtNavHistory = (TextView) findViewById(R.id.txtNavHistory);
        txtNavFavorites = (TextView) findViewById(R.id.txtNavFavorites);

        // Drawer Actions
        layoutDrawerOffline = (LinearLayout) findViewById(R.id.layoutDrawerOffline);
        layoutDrawerAi = (LinearLayout) findViewById(R.id.layoutDrawerAi);
        layoutDrawerShare = (LinearLayout) findViewById(R.id.layoutDrawerShare);
        layoutDrawerLogout = (LinearLayout) findViewById(R.id.layoutDrawerLogout);
    }

    void thietLapDrawer() {
        // Cap nhat thong tin nguoi dung tren Drawer
        capNhatDrawerUser();

        // 1. Chon Profile header
        if (layoutDrawerUser != null) {
            layoutDrawerUser.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    QLTaiKhoan ql = QLTaiKhoan.getInstance(MainActivity.this);
                    if (ql.daDangNhap()) {
                        chonTab(R.id.nav_settings);
                    } else {
                        Intent i = new Intent(MainActivity.this, DangNhapActivity.class);
                        startActivity(i);
                    }
                }
            });
        }

        // 2. Chon Upgrade to Premium card
        if (cardDrawerUpgrade != null) {
            cardDrawerUpgrade.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    QLTaiKhoan ql = QLTaiKhoan.getInstance(MainActivity.this);
                    if (!ql.daDangNhap()) {
                        Toast.makeText(MainActivity.this, "Vui lòng đăng nhập tài khoản trước khi nâng cấp gói AI.", Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(MainActivity.this, DangNhapActivity.class);
                        startActivity(i);
                        return;
                    }
                    chonTab(R.id.nav_settings);
                    if (settingsFragment != null) {
                        settingsFragment.hienDialogNangCapGoi();
                    }
                }
            });
        }

        // 3. Chon Home
        if (layoutDrawerHome != null) {
            layoutDrawerHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    chonTab(R.id.nav_home);
                }
            });
        }

        // 4. Chon My Files
        if (layoutDrawerFiles != null) {
            layoutDrawerFiles.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    chonTab(R.id.nav_files);
                }
            });
        }

        // 5. Chon History
        if (layoutDrawerHistory != null) {
            layoutDrawerHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    chonTab(R.id.nav_history);
                }
            });
        }

        // 6. Chon Favorites
        if (layoutDrawerFavorites != null) {
            layoutDrawerFavorites.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    chonTab(R.id.nav_files);
                }
            });
        }

        // 7. Chon Offline Extraction
        if (layoutDrawerOffline != null) {
            layoutDrawerOffline.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    chonTab(R.id.nav_home);
                    if (homeFragment != null) {
                        homeFragment.batModeAi(false);
                    }
                }
            });
        }

        // 8. Chon AI Extraction
        if (layoutDrawerAi != null) {
            layoutDrawerAi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    chonTab(R.id.nav_home);
                    if (homeFragment != null) {
                        homeFragment.batModeAi(true);
                    }
                }
            });
        }

        // 9. Chon Share App
        if (layoutDrawerShare != null) {
            layoutDrawerShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "SmartDocs");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "SmartDocs - Ứng dụng quét tài liệu & trích xuất văn bản AI thông minh. Tải ngay!");
                    startActivity(Intent.createChooser(sendIntent, "Chia sẻ ứng dụng"));
                }
            });
        }

        // 10. Chon Dang xuat
        if (layoutDrawerLogout != null) {
            layoutDrawerLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dongDrawer();
                    final QLTaiKhoan ql = QLTaiKhoan.getInstance(MainActivity.this);
                    if (!ql.daDangNhap()) {
                        Toast.makeText(MainActivity.this, "Bạn chưa đăng nhập tài khoản.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DialogHelper.showConfirm(MainActivity.this, "Đăng xuất", "Bạn có chắc chắn muốn đăng xuất tài khoản AI?", "Đăng xuất", new DialogHelper.OnConfirm() {
                        @Override
                        public void onConfirm() {
                            ql.dangXuat();
                            capNhatDrawerUser();
                            if (homeFragment != null) {
                                homeFragment.capNhatAvatarHome();
                            }
                            if (settingsFragment != null) {
                                settingsFragment.capNhatGiaoDienTaiKhoan();
                            }
                            Toast.makeText(MainActivity.this, "Đã đăng xuất tài khoản.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    public void moDrawer() {
        if (drawerLayout != null) {
            capNhatDrawerUser();
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void dongDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    public void capNhatDrawerUser() {
        if (txtDrawerTen == null) return;
        QLTaiKhoan ql = QLTaiKhoan.getInstance(this);
        if (ql.daDangNhap()) {
            String email = ql.getEmail();
            txtDrawerEmail.setText(email);
            String ten = email;
            if (email.contains("@")) {
                ten = email.substring(0, email.indexOf("@"));
            }
            txtDrawerTen.setText(ten);
            if (!email.isEmpty()) {
                txtDrawerAvatarChar.setText(email.substring(0, 1).toUpperCase());
            }
            String loaiGoi = ql.getLoaiGoi();
            if ("VIP".equalsIgnoreCase(loaiGoi)) {
                txtDrawerGoiBadge.setText("Gói VIP");
            } else if ("PRO".equalsIgnoreCase(loaiGoi)) {
                txtDrawerGoiBadge.setText("Gói Siêu cấp Pro");
            } else if ("PLUS".equalsIgnoreCase(loaiGoi)) {
                txtDrawerGoiBadge.setText("Gói Nâng cấp Plus");
            } else {
                txtDrawerGoiBadge.setText("Gói Miễn phí");
            }
        } else {
            txtDrawerTen.setText("Tài khoản khách");
            txtDrawerEmail.setText("Chưa đăng nhập tài khoản");
            txtDrawerGoiBadge.setText("Gói Miễn phí");
            txtDrawerAvatarChar.setText("T");
        }
    }

    void capNhatDrawerActiveItem(int tabId) {
        int mauActiveBg = R.drawable.bg_nav_item_selected;
        int mauChinh = Color.parseColor("#60A5FA");
        int mauTrang = Color.parseColor("#FFFFFF");
        int mauInactive = Color.parseColor("#E2E8F0");

        // Reset tat ca ve unselected
        if (layoutDrawerHome != null) layoutDrawerHome.setBackgroundColor(Color.TRANSPARENT);
        if (layoutDrawerFiles != null) layoutDrawerFiles.setBackgroundColor(Color.TRANSPARENT);
        if (layoutDrawerHistory != null) layoutDrawerHistory.setBackgroundColor(Color.TRANSPARENT);
        if (layoutDrawerFavorites != null) layoutDrawerFavorites.setBackgroundColor(Color.TRANSPARENT);

        if (imgNavHome != null) imgNavHome.setColorFilter(mauInactive);
        if (imgNavFiles != null) imgNavFiles.setColorFilter(mauInactive);
        if (imgNavHistory != null) imgNavHistory.setColorFilter(mauInactive);
        if (imgNavFavorites != null) imgNavFavorites.setColorFilter(mauInactive);

        if (txtNavHome != null) txtNavHome.setTextColor(mauInactive);
        if (txtNavFiles != null) txtNavFiles.setTextColor(mauInactive);
        if (txtNavHistory != null) txtNavHistory.setTextColor(mauInactive);
        if (txtNavFavorites != null) txtNavFavorites.setTextColor(mauInactive);

        // Highlight tab dang chon
        if (tabId == R.id.nav_home && layoutDrawerHome != null) {
            layoutDrawerHome.setBackgroundResource(mauActiveBg);
            if (imgNavHome != null) imgNavHome.setColorFilter(mauChinh);
            if (txtNavHome != null) txtNavHome.setTextColor(mauTrang);
        } else if (tabId == R.id.nav_files && layoutDrawerFiles != null) {
            layoutDrawerFiles.setBackgroundResource(mauActiveBg);
            if (imgNavFiles != null) imgNavFiles.setColorFilter(mauChinh);
            if (txtNavFiles != null) txtNavFiles.setTextColor(mauTrang);
        } else if (tabId == R.id.nav_history && layoutDrawerHistory != null) {
            layoutDrawerHistory.setBackgroundResource(mauActiveBg);
            if (imgNavHistory != null) imgNavHistory.setColorFilter(mauChinh);
            if (txtNavHistory != null) txtNavHistory.setTextColor(mauTrang);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        capNhatDrawerUser();
        if (homeFragment != null) {
            homeFragment.capNhatAvatarHome();
        }
    }

    void khoiTaoFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
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
                capNhatDrawerActiveItem(id);
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

    public void chonTab(int tabId) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(tabId);
        }
        capNhatDrawerActiveItem(tabId);
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

    void thietLapXuLyBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Neu Navigation Drawer dang mo thi dong drawer truoc
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }

                // Neu tab Files dang bat multi-select thi thoat che do chon nhieu truoc
                if (activeFragment == filesFragment && filesFragment != null && filesFragment.xuLyBack()) {
                    return;
                }

                // Neu dang o tab phu -> bam Back chuyen ve Home
                if (activeFragment != homeFragment) {
                    chonTab(R.id.nav_home);
                    return;
                }

                // Dang o Home tab -> dong ung dung
                finish();
            }
        });
    }
}

