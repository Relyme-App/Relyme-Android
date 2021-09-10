package lahds.relyme.UserInterface.EmojiView.Adapters;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import lahds.relyme.UserInterface.EmojiView.EmojiManager;
import lahds.relyme.UserInterface.EmojiView.Listener.OnStickerActions;
import lahds.relyme.UserInterface.EmojiView.Sticker.RecentSticker;
import lahds.relyme.UserInterface.EmojiView.Sticker.Sticker;
import lahds.relyme.UserInterface.EmojiView.Sticker.StickerLoader;
import lahds.relyme.UserInterface.EmojiView.Utils.Utils;

public class RecentStickerRecyclerAdapter extends RecyclerView.Adapter<RecentStickerRecyclerAdapter.ViewHolder> {
    RecentSticker recent;
    OnStickerActions events;
    StickerLoader loader;

    public RecentStickerRecyclerAdapter(RecentSticker recent, OnStickerActions events, StickerLoader loader){
        this.recent = recent;
        this.events = events;
        this.loader = loader;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        FrameLayout frameLayout = new FrameLayout(viewGroup.getContext());
        View emojiView = EmojiManager.getInstance().getStickerViewCreatorListener().onCreateStickerView(viewGroup.getContext(),null,true);
        int cw = Utils.getStickerColumnWidth(viewGroup.getContext());
        frameLayout.setLayoutParams(new FrameLayout.LayoutParams(cw,cw));
        frameLayout.addView(emojiView);

        int dp6=Utils.dpToPx(viewGroup.getContext(),6);
        emojiView.setPadding(dp6,dp6,dp6,dp6);

        View ripple = new View(viewGroup.getContext());
        frameLayout.addView(ripple,new ViewGroup.MarginLayoutParams(cw,cw));

        return new ViewHolder(frameLayout);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        FrameLayout frameLayout = (FrameLayout) viewHolder.itemView;
        final AppCompatImageView stickerView = (AppCompatImageView) frameLayout.getChildAt(0);
        View ripple = (View) frameLayout.getChildAt(1);

        @SuppressWarnings("rawtypes")
		final Sticker sticker = (Sticker) recent.getRecentStickers().toArray()[i];
        loader.onLoadSticker(stickerView,sticker);

        Utils.setClickEffect(ripple,false);

        ripple.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (events !=null) events.onClick(stickerView,sticker,true);
            }
        });
            ripple.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (events!=null) return events.onLongClick(stickerView,sticker,true);
                    return false;
                }
            });
    }

    @Override
    public int getItemCount() {
        return recent.getRecentStickers().size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
