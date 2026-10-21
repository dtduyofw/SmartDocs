package com.example.smartdocs.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartdocs.R;
import com.example.smartdocs.data.model.ScanDocument;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/*
  RecyclerView Adapter cho danh sách lịch sử tài liệu.
  Dùng ListAdapter + DiffUtil để tự động animate thay đổi.
  Nhóm 2: thêm star toggle, menu ⋮, hiển thị số trang.
 */
public class DocumentAdapter extends ListAdapter<ScanDocument, DocumentAdapter.DocumentViewHolder> {

    OnItemClickListener clickListener;
    OnItemLongClickListener longClickListener;
    OnStarClickListener starClickListener;
    OnMenuClickListener menuClickListener;

    // DiffUtil
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

    // Callback interfaces
    public interface OnItemClickListener {
        void onItemClick(ScanDocument document, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ScanDocument document, int position);
    }

    public interface OnStarClickListener {
        void onStarClick(ScanDocument document, int position);
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

    public void setOnStarClickListener(OnStarClickListener listener) {
        this.starClickListener = listener;
    }

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    // RecyclerView lifecycle
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
        holder.bind(document, clickListener, longClickListener, starClickListener, menuClickListener);
    }

    // ViewHolder
    static class DocumentViewHolder extends RecyclerView.ViewHolder {

        TextView txtItemTitle;
        TextView txtItemOcrPreview;
        TextView txtItemDate;
        Chip chipAi;
        View cardItemIcon;
        android.widget.ImageView imgStar;
        android.widget.ImageView imgMenu;

        static final SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());

        DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            txtItemTitle    = (TextView) itemView.findViewById(R.id.txtItemTitle);
            txtItemOcrPreview = (TextView) itemView.findViewById(R.id.txtItemOcrPreview);
            txtItemDate     = (TextView) itemView.findViewById(R.id.txtItemDate);
            chipAi          = (Chip) itemView.findViewById(R.id.chipAi);
            cardItemIcon    = itemView.findViewById(R.id.cardItemIcon);
            imgStar         = (android.widget.ImageView) itemView.findViewById(R.id.imgStar);
            imgMenu         = (android.widget.ImageView) itemView.findViewById(R.id.imgMenu);
        }

        void bind(ScanDocument document,
                  OnItemClickListener clickListener,
                  OnItemLongClickListener longClickListener,
                  OnStarClickListener starClickListener,
                  OnMenuClickListener menuClickListener) {

            //Tiêu đề
            txtItemTitle.setText(
                    document.title != null && !document.title.isEmpty()
                            ? document.title : "Tài liệu không tiêu đề"
            );

            //Preview OCR
            if (document.ocrText != null && !document.ocrText.isEmpty()) {
                String preview = document.ocrText.trim();
                if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                txtItemOcrPreview.setText(preview);
                txtItemOcrPreview.setVisibility(View.VISIBLE);
            } else {
                txtItemOcrPreview.setVisibility(View.GONE);
            }

            // Badge AI
            boolean coAi = document.aiSummary != null && !document.aiSummary.trim().isEmpty();
            chipAi.setVisibility(coAi ? View.VISIBLE : View.GONE);

            //Ngày giờ + số trang
            String ngay = dateFormat.format(new Date(document.createdAt));
            String soTrang = document.pageCount + " trang";
            txtItemDate.setText(ngay + " • " + soTrang);

            // Ẩn icon cái bút ở lịch sử quét
            if (cardItemIcon != null) {
                cardItemIcon.setVisibility(View.GONE);
            }

            // Star icon — ẩn trong danh sách lịch sử quét
            if (imgStar != null) {
                imgStar.setVisibility(View.GONE);
            }

            // Menu
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

            //Click toàn item
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = getAdapterPosition();
                    if (clickListener != null && pos != RecyclerView.NO_ID) {
                        clickListener.onItemClick(document, pos);
                    }
                }
            });

            //Long click
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
