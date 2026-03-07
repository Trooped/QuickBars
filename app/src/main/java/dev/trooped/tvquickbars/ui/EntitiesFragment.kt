package dev.trooped.tvquickbars.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.trooped.tvquickbars.R
import com.google.android.material.card.MaterialCardView

/**
 * EntitiesFragment
 *
 * This fragment displays options for managing entities in the application.
 * It provides buttons to import entities and manage saved entities.
 */
class EntitiesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_entities, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val importCard: MaterialCardView = view.findViewById(R.id.import_card)
        val manageCard: MaterialCardView = view.findViewById(R.id.manage_card)

        // Make cards clickable and add ripple effect
        importCard.isClickable = true
        importCard.isFocusable = true
        manageCard.isClickable = true
        manageCard.isFocusable = true

        importCard.setOnClickListener {
            // This will launch our existing EntityImporterActivity to manage the global entity list
            val intent = Intent(requireActivity(), EntityImporterActivity::class.java)
            startActivity(intent)
        }

        manageCard.setOnClickListener {
            val intent = Intent(requireActivity(), ManageSavedEntitiesActivity::class.java)
            startActivity(intent)
        }
    }
}
