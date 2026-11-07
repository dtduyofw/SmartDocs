package com.example.smartdocs.ui.adapter;

import android.net.Uri;
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
                    return oldItem.title.equals(newItem.title)
                            && oldItem.ocrText.equals(newItem.ocrText)
                            && oldItem.createdAt == newItem.createdAt;
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
                txtItemTitle.setText(
                        document.title != null && !document.title.isEmpty()
                                ? document.title : "Tài liệu không tiêu đề"
                );
            }

            // Preview van ban OCR (toi da 2 dong)
            if (txtItemOcrPreview != null) {
                if (document.ocrText != null && !document.ocrText.isEmpty()) {
                    String preview = document.ocrText.trim();
                    if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                    txtItemOcrPreview.setText(preview);
                    txtItemOcrPreview.setVisibility(View.VISIBLE);
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
                    String formattedTags = "#" + document.tags.trim().replace(",", " #");
                    txtBadgeTag.setText(formattedTags);
                    txtBadgeTag.setVisibility(View.VISIBLE);
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
