package net.crunkhouse.shaitzov;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * An ItemDecorator to overlap cards.
 */

public class PileOverlapDecorator extends RecyclerView.ItemDecoration {

    private int overlapAmount;

    public PileOverlapDecorator(Context context) {
        overlapAmount = DensityUtils.dpToPixel(context, -70);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        final int itemPosition = parent.getChildAdapterPosition(view);
        // We don't want to apply the offset to the first item
        if (itemPosition == RecyclerView.NO_POSITION || itemPosition == 0) {
            return;
        }
        outRect.left = overlapAmount;
    }
}
