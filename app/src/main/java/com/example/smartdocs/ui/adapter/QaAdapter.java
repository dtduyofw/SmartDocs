package com.example.smartdocs.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.smartdocs.R;
import com.example.smartdocs.data.model.QaPair;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/*
  QaAdapter — Adapter cho ListView hiển thị lịch sử Q&A dạng chat bubble.
  Mỗi item có 2 bubble (user + AI), chỉ một trong hai hiện tại một thời điểm dựa trên field QaPair.isUser.
  Không dùng ViewHolder pattern.
 */
public class QaAdapter extends ArrayAdapter<QaPair> {
    public QaAdapter(Context context, List<QaPair> list) {
        super(context, 0, list);
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Inflate layout nếu chưa có
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_qa_bubble, parent, false);
        }

        QaPair item = getItem(position);

        MaterialCardView cardUser = (MaterialCardView) convertView.findViewById(R.id.cardUser);
        MaterialCardView cardAi = (MaterialCardView) convertView.findViewById(R.id.cardAi);
        TextView txtUserMessage = (TextView) convertView.findViewById(R.id.txtUserMessage);
        TextView txtAiMessage = (TextView) convertView.findViewById(R.id.txtAiMessage);

        if (item != null && item.isUser) {
            // Tin nhắn người dùng — hiện bubble phải, ẩn bubble trái
            cardUser.setVisibility(View.VISIBLE);
            cardAi.setVisibility(View.GONE);
            txtUserMessage.setText(item.noiDung);
        } else if (item != null) {
            // Tin nhắn AI — hiện bubble trái, ẩn bubble phải
            cardUser.setVisibility(View.GONE);
            cardAi.setVisibility(View.VISIBLE);
            txtAiMessage.setText(item.noiDung);
        }

        return convertView;
    }
}
