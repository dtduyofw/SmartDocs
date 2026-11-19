package com.example.smartdocs.ui.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartdocs.R;
import com.example.smartdocs.data.model.ScanDocument;
import com.example.smartdocs.ocr.DocumentClassifierHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Adapter danh sach lich su tai lieu quet theo mau the co thumbnail
public class DocumentAdapter extends ListAdapter<ScanDocument, DocumentAdapter.DocumentViewHolder> {

    OnItemClickListener clickListener;
    OnItemLongClickListener longClickListener;
    OnMenuClickListener menuClickListener;

    static final DiffUtil.ItemCallback<ScanDocument> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ScanDocument>() {
                @Override
                public boolean areItemsTheSame(@NonNull ScanDocument oldItem,
                                               @NonNull ScanDocument newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ScanDocument oldItem,
                                                  @NonNull ScanDocument newItem) {
                    return java.util.Objects.equals(oldItem.title, newItem.title)
                            && java.util.Objects.equals(oldItem.ocrText, newItem.ocrText)
                            && java.util.Objects.equals(oldItem.tags, newItem.tags)
                            && java.util.Objects.equals(oldItem.aiSummary, newItem.aiSummary)
                            && oldItem.createdAt == newItem.createdAt
                            && oldItem.pageCount == newItem.pageCount;
                }
            };

    public DocumentAdapter() {
        super(DIFF_CALLBACK);
    }

    public interface OnItemClickListener {
        void onItemClick(ScanDocument document, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ScanDocument document, int position);
    }

    public interface OnMenuClickListener {
        void onMenuClick(ScanDocument document, int position, View anchorView);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    // Loai bo the HTML va giai ma ky tu HTML ve text thuan
    public static String loaiBoHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        String text;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
            } else {
                text = Html.fromHtml(html).toString();
            }
        } catch (Exception e) {
            text = html;
        }
        // Xoa cac the HTML con sot lai
        text = text.replaceAll("<[^>]*>", " ");
        // Chuan hoa khoang trang ngang
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        // Rut gon nhieu dong trong thanh 1 dong
        text = text.replaceAll("\\n\\s*\\n+", "\n");
        return text.trim();
    }

    // Chuan hoa tieu de tai lieu: loai bo the HTML va loai bo ky tu xuong dong
    public static String chuanHoaTieuDe(String rawTitle) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            return "Tài liệu không tiêu đề";
        }
        String clean = loaiBoHtml(rawTitle);
        clean = clean.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? "Tài liệu không tiêu đề" : clean;
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document, parent, false);
        return new DocumentViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        ScanDocument document = getItem(position);
        holder.bind(document, clickListener, longClickListener, menuClickListener);
    }

    static class DocumentViewHolder extends RecyclerView.ViewHolder {

        ImageView imgDocThumbnail;
        TextView txtBadgePageCount;
        TextView txtItemTitle;
        TextView txtItemOcrPreview;
        TextView txtBadgeAi;
        TextView txtBadgeLanguage;
        TextView txtBadgeTag;
        TextView txtItemDate;
        ImageView imgMenu;

        static final SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());

        DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            imgDocThumbnail    = (ImageView) itemView.findViewById(R.id.imgDocThumbnail);
            txtBadgePageCount  = (TextView) itemView.findViewById(R.id.txtBadgePageCount);
            txtItemTitle       = (TextView) itemView.findViewById(R.id.txtItemTitle);
            txtItemOcrPreview  = (TextView) itemView.findViewById(R.id.txtItemOcrPreview);
            txtBadgeAi         = (TextView) itemView.findViewById(R.id.txtBadgeAi);
            txtBadgeLanguage   = (TextView) itemView.findViewById(R.id.txtBadgeLanguage);
            txtBadgeTag        = (TextView) itemView.findViewById(R.id.txtBadgeTag);
            txtItemDate        = (TextView) itemView.findViewById(R.id.txtItemDate);
            imgMenu            = (ImageView) itemView.findViewById(R.id.imgMenu);
        }

        void bind(ScanDocument document,
                  OnItemClickListener clickListener,
                  OnItemLongClickListener longClickListener,
                  OnMenuClickListener menuClickListener) {

            // Anh thu nho trang dau cua tai lieu
            if (imgDocThumbnail != null) {
                if (document.imageUri != null && !document.imageUri.isEmpty()) {
                    try {
                        imgDocThumbnail.setImageURI(Uri.parse(document.imageUri));
                    } catch (Exception e) {
                        imgDocThumbnail.setImageResource(R.drawable.ic_gallery);
                    }
                } else {
                    imgDocThumbnail.setImageResource(R.drawable.ic_gallery);
                }
            }

            // Badge so trang de goc anh
            if (txtBadgePageCount != null) {
                if (document.pageCount > 1) {
                    txtBadgePageCount.setText(document.pageCount + " trang");
                    txtBadgePageCount.setVisibility(View.VISIBLE);
                } else {
                    txtBadgePageCount.setVisibility(View.GONE);
                }
            }

            // Tieu de tai lieu
            if (txtItemTitle != null) {
                String tieuDe = chuanHoaTieuDe(document.title);
                txtItemTitle.setText(tieuDe);
            }

            // Preview van ban OCR (toi da 2 dong)
            if (txtItemOcrPreview != null) {
                if (document.ocrText != null && !document.ocrText.isEmpty()) {
                    String preview = loaiBoHtml(document.ocrText);
                    if (!preview.isEmpty()) {
                        if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                        txtItemOcrPreview.setText(preview);
                        txtItemOcrPreview.setVisibility(View.VISIBLE);
                    } else {
                        txtItemOcrPreview.setVisibility(View.GONE);
                    }
                } else {
                    txtItemOcrPreview.setVisibility(View.GONE);
                }
            }

            // Badge AI
            if (txtBadgeAi != null) {
                boolean coAi = document.aiSummary != null && !document.aiSummary.trim().isEmpty();
                txtBadgeAi.setVisibility(coAi ? View.VISIBLE : View.GONE);
            }

            // Badge Ngon ngu
            if (txtBadgeLanguage != null) {
                if (document.language != null && !document.language.trim().isEmpty() && !"auto".equalsIgnoreCase(document.language)) {
                    String lang = document.language.trim();
                    if ("vi".equalsIgnoreCase(lang) || "vie".equalsIgnoreCase(lang)) {
                        lang = "Tiếng Việt";
                    } else if ("en".equalsIgnoreCase(lang) || "eng".equalsIgnoreCase(lang)) {
                        lang = "Tiếng Anh";
                    }
                    txtBadgeLanguage.setText(lang);
                    txtBadgeLanguage.setVisibility(View.VISIBLE);
                } else {
                    txtBadgeLanguage.setVisibility(View.GONE);
                }
            }

            // Badge Tag phan loai
            if (txtBadgeTag != null) {
                if (document.tags != null && !document.tags.trim().isEmpty()) {
                    String tag = document.tags.trim();
                    txtBadgeTag.setText("#" + tag.replace(",", " #"));
                    txtBadgeTag.setVisibility(View.VISIBLE);

                    // To mau badge rieng biet cho tung loai tai lieu
                    GradientDrawable bgBadge = new GradientDrawable();
                    float density = itemView.getResources().getDisplayMetrics().density;
                    bgBadge.setCornerRadius(6 * density);

                    if (tag.contains(DocumentClassifierHelper.TAG_HOA_DON)) {
                        bgBadge.setColor(Color.parseColor("#2A2015"));
                        txtBadgeTag.setTextColor(Color.parseColor("#F59E0B"));
                    } else if (tag.contains(DocumentClassifierHelper.TAG_GIAY_TO)) {
                        bgBadge.setColor(Color.parseColor("#172033"));
                        txtBadgeTag.setTextColor(Color.parseColor("#3B82F6"));
                    } else if (tag.contains(DocumentClassifierHelper.TAG_HOC_TAP)) {
                        bgBadge.setColor(Color.parseColor("#142820"));
                        txtBadgeTag.setTextColor(Color.parseColor("#10B981"));
                    } else if (tag.contains(DocumentClassifierHelper.TAG_SO_LIEU)) {
                        bgBadge.setColor(Color.parseColor("#261C3B"));
                        txtBadgeTag.setTextColor(Color.parseColor("#A855F7"));
                    } else {
                        bgBadge.setColor(Color.parseColor("#27272A"));
                        txtBadgeTag.setTextColor(Color.parseColor("#A1A1AA"));
                    }
                    txtBadgeTag.setBackground(bgBadge);
                    txtBadgeTag.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));
                } else {
                    txtBadgeTag.setVisibility(View.GONE);
                }
            }

            // Ngay gio quet
            if (txtItemDate != null) {
                String ngay = dateFormat.format(new Date(document.createdAt));
                txtItemDate.setText(ngay);
            }

            // Menu 3 cham
            if (imgMenu != null) {
                imgMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int pos = getAdapterPosition();
                        if (menuClickListener != null && pos != RecyclerView.NO_ID) {
                            menuClickListener.onMenuClick(document, pos, v);
                        }
                    }
                });
            }

            // Click xem chi tiet
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = getAdapterPosition();
                    if (clickListener != null && pos != RecyclerView.NO_ID) {
                        clickListener.onItemClick(document, pos);
                    }
                }
            });

            // Nhan giu item
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    int pos = getAdapterPosition();
                    if (longClickListener != null && pos != RecyclerView.NO_ID) {
                        longClickListener.onItemLongClick(document, pos);
                    }
                    return true;
                }
            });
        }
    }
}
