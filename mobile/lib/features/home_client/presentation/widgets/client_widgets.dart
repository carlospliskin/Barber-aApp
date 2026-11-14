import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/client_entities.dart';

// ─── BarberiaCard — tarjeta de búsqueda ──────────────────────────────────────

class BarberiaCard extends StatelessWidget {
  final BarberiaInfo barberia;
  final VoidCallback onTap;
  final VoidCallback? onFavorite;

  const BarberiaCard({
    super.key,
    required this.barberia,
    required this.onTap,
    this.onFavorite,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.base),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: barberia.isFavorite
                ? AppColors.gold.withOpacity(0.3)
                : AppColors.smoke,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                // Avatar barbería
                Container(
                  width: 44, height: 44,
                  decoration: BoxDecoration(
                    gradient: AppColors.goldGradient,
                    borderRadius: AppRadius.rSm,
                  ),
                  child: Center(
                    child: Text(
                      barberia.name[0].toUpperCase(),
                      style: AppTypography.displayMd.copyWith(
                        color: AppColors.obsidian,
                        fontSize: 20,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(barberia.name,
                        style: AppTypography.headingSm),
                      const SizedBox(height: 2),
                      Row(
                        children: [
                          // Rating
                          if (barberia.avgRating > 0) ...[
                            const Icon(Icons.star_rounded,
                              size: 12, color: AppColors.gold),
                            const SizedBox(width: 2),
                            Text(
                              barberia.avgRating.toStringAsFixed(1),
                              style: AppTypography.monoSm.copyWith(
                                color: AppColors.gold, fontSize: 11),
                            ),
                            Text(
                              ' (${barberia.reviewCount})',
                              style: AppTypography.bodySm.copyWith(fontSize: 10),
                            ),
                            const SizedBox(width: AppSpacing.sm),
                          ],
                          // Estado abierto/cerrado
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 1),
                            decoration: BoxDecoration(
                              color: barberia.isOpen
                                  ? AppColors.confirmedSubtle
                                  : AppColors.errorSubtle,
                              borderRadius: AppRadius.rPill,
                            ),
                            child: Text(
                              barberia.isOpen ? 'Abierto' : 'Cerrado',
                              style: AppTypography.overline.copyWith(
                                color: barberia.isOpen
                                    ? AppColors.confirmed
                                    : AppColors.error,
                                fontSize: 8,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                // Favorito
                GestureDetector(
                  onTap: () {
                    HapticFeedback.lightImpact();
                    onFavorite?.call();
                  },
                  child: Padding(
                    padding: const EdgeInsets.all(AppSpacing.xs),
                    child: Icon(
                      barberia.isFavorite
                          ? Icons.favorite_rounded
                          : Icons.favorite_border_rounded,
                      size: 20,
                      color: barberia.isFavorite
                          ? AppColors.gold
                          : AppColors.ash,
                    ),
                  ),
                ),
              ],
            ),

            if (barberia.services.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              Container(height: 1, color: AppColors.smoke),
              const SizedBox(height: AppSpacing.md),
              // Preview de servicios (máx 3)
              Wrap(
                spacing: AppSpacing.xs,
                runSpacing: AppSpacing.xs,
                children: barberia.services.take(3).map((s) =>
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.sm, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.slate,
                      borderRadius: AppRadius.rPill,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(s.name,
                          style: AppTypography.labelSm.copyWith(
                            color: AppColors.pearl, fontSize: 10)),
                        const SizedBox(width: 4),
                        Text(s.priceLabel,
                          style: AppTypography.monoSm.copyWith(
                            fontSize: 10)),
                      ],
                    ),
                  ),
                ).toList()
                ..addAll([
                  if (barberia.services.length > 3)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpacing.sm, vertical: 3),
                      decoration: BoxDecoration(
                        color: AppColors.slate,
                        borderRadius: AppRadius.rPill,
                      ),
                      child: Text('+${barberia.services.length - 3} más',
                        style: AppTypography.labelSm.copyWith(
                          color: AppColors.ash, fontSize: 10)),
                    ),
                ]),
              ),
            ],

            // Disponibilidad
            if (barberia.hasAvailability) ...[
              const SizedBox(height: AppSpacing.sm),
              Row(
                children: [
                  const Icon(Icons.schedule_rounded,
                    size: 12, color: AppColors.confirmed),
                  const SizedBox(width: 4),
                  Text('Horarios disponibles hoy',
                    style: AppTypography.labelSm.copyWith(
                      color: AppColors.confirmed, fontSize: 10)),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// ─── LoyaltyProgressCard — puntos del cliente ────────────────────────────────

class LoyaltyProgressCard extends StatelessWidget {
  final ClientLoyaltyStatus loyalty;

  const LoyaltyProgressCard({super.key, required this.loyalty});

  @override
  Widget build(BuildContext context) {
    return GoldGlowContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 36, height: 36,
                decoration: BoxDecoration(
                  color: AppColors.goldSubtle,
                  borderRadius: AppRadius.rSm,
                ),
                child: const Icon(Icons.star_rounded,
                  color: AppColors.gold, size: 20),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(loyalty.programName.isNotEmpty
                        ? loyalty.programName
                        : 'Mi progreso',
                      style: AppTypography.headingSm),
                    Text(
                      '${loyalty.visitsCount} visitas · '
                      '${loyalty.totalVisitsEver} en total',
                      style: AppTypography.bodySm.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ),
            ],
          ),

          if (loyalty.rulesProgress.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.md),
            Container(height: 1, color: AppColors.gold.withOpacity(0.2)),
            const SizedBox(height: AppSpacing.md),
            ...loyalty.rulesProgress.map((rule) =>
              _RuleProgressRow(rule: rule)),
          ] else ...[
            const SizedBox(height: AppSpacing.md),
            Text(
              'Completa citas para acumular puntos',
              style: AppTypography.bodySm.copyWith(color: AppColors.ash),
            ),
          ],
        ],
      ),
    );
  }
}

class _RuleProgressRow extends StatelessWidget {
  final LoyaltyRuleProgress rule;
  const _RuleProgressRow({required this.rule});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(rule.ruleName,
                  style: AppTypography.labelLg.copyWith(
                    color: rule.isCompleted
                        ? AppColors.gold
                        : AppColors.white,
                  )),
              ),
              if (rule.isCompleted)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.sm, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.goldSubtle,
                    borderRadius: AppRadius.rPill,
                  ),
                  child: Text('¡Disponible!',
                    style: AppTypography.overline.copyWith(
                      color: AppColors.gold, fontSize: 8,
                      fontWeight: FontWeight.w700)),
                )
              else
                Text(
                  '${rule.visitsRemaining} visita${rule.visitsRemaining != 1 ? 's' : ''} más',
                  style: AppTypography.monoSm.copyWith(
                    color: AppColors.ash, fontSize: 10),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          // Barra de progreso
          ClipRRect(
            borderRadius: AppRadius.rPill,
            child: LinearProgressIndicator(
              value: rule.progressPercent,
              backgroundColor: AppColors.smoke,
              valueColor: const AlwaysStoppedAnimation<Color>(AppColors.gold),
              minHeight: 4,
            ),
          ),
          const SizedBox(height: 4),
          Text(rule.rewardLabel,
            style: AppTypography.labelSm.copyWith(
              color: AppColors.ash, fontSize: 9)),
        ],
      ),
    );
  }
}

// ─── ClientAppointmentCard — cita del cliente ─────────────────────────────────

class ClientAppointmentCard extends StatelessWidget {
  final ClientAppointment appointment;
  final VoidCallback? onCancel;
  final VoidCallback? onTap;

  const ClientAppointmentCard({
    super.key,
    required this.appointment,
    this.onCancel,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md, vertical: AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: appointment.status == ClientAppointmentStatus.confirmed
                ? AppColors.confirmed.withOpacity(0.3)
                : appointment.status == ClientAppointmentStatus.pendingConfirmation
                    ? AppColors.gold.withOpacity(0.3)
                    : AppColors.smoke,
          ),
        ),
        child: Row(
          children: [
            // Bloque de tiempo
            Container(
              width: 52,
              padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
              decoration: BoxDecoration(
                color: appointment.status.isActive
                    ? AppColors.goldSubtle : AppColors.slate,
                borderRadius: AppRadius.rSm,
              ),
              child: Column(
                children: [
                  Text(appointment.startTimeFormatted,
                    style: AppTypography.monoMd.copyWith(
                      color: appointment.status.isActive
                          ? AppColors.gold : AppColors.ash,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 2),
                  Text('${appointment.durationMinutes}m',
                    style: AppTypography.monoSm.copyWith(fontSize: 9),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            // Info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(appointment.serviceName,
                    style: AppTypography.headingSm.copyWith(fontSize: 13),
                    overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 2),
                  Text(
                    '${appointment.barberName} · \$${appointment.basePrice}',
                    style: AppTypography.bodySm,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (appointment.requiresPayment) ...[
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(Icons.payment_rounded,
                          size: 11, color: AppColors.gold),
                        const SizedBox(width: 3),
                        Text('Depósito pendiente',
                          style: AppTypography.labelSm.copyWith(
                            color: AppColors.gold, fontSize: 9)),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            // Estado + acciones
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                AppBadge(
                  label: appointment.status.label,
                  variant: _badgeVariant(appointment.status),
                ),
                if (appointment.status.canCancel && onCancel != null) ...[
                  const SizedBox(height: 6),
                  GestureDetector(
                    onTap: onCancel,
                    child: Text('Cancelar',
                      style: AppTypography.labelSm.copyWith(
                        color: AppColors.error, fontSize: 10)),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  AppBadgeVariant _badgeVariant(ClientAppointmentStatus s) => switch (s) {
    ClientAppointmentStatus.confirmed           => AppBadgeVariant.confirmed,
    ClientAppointmentStatus.pendingConfirmation => AppBadgeVariant.pending,
    ClientAppointmentStatus.completed           => AppBadgeVariant.confirmed,
    ClientAppointmentStatus.cancelled           => AppBadgeVariant.cancelled,
    ClientAppointmentStatus.noShow              => AppBadgeVariant.noShow,
  };
}

// ─── SlotPicker — selector de horario disponible ─────────────────────────────

class SlotPicker extends StatelessWidget {
  final List<AvailableSlot> slots;
  final AvailableSlot? selected;
  final ValueChanged<AvailableSlot> onSelected;

  const SlotPicker({
    super.key,
    required this.slots,
    required this.selected,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    if (slots.isEmpty) {
      return Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Center(
          child: Text('Sin horarios disponibles',
            style: AppTypography.bodyMd.copyWith(color: AppColors.ash)),
        ),
      );
    }

    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: slots.map((slot) {
        final isSelected = selected?.time == slot.time;
        return GestureDetector(
          onTap: () {
            HapticFeedback.selectionClick();
            onSelected(slot);
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md, vertical: AppSpacing.sm),
            decoration: BoxDecoration(
              gradient: isSelected ? AppColors.goldGradient : null,
              color: isSelected ? null : AppColors.graphite,
              borderRadius: AppRadius.rSm,
              border: Border.all(
                color: isSelected
                    ? AppColors.gold : AppColors.smoke),
              boxShadow: isSelected ? AppColors.shadowGoldBtn : null,
            ),
            child: Text(
              slot.time,
              style: AppTypography.monoMd.copyWith(
                color: isSelected ? AppColors.obsidian : AppColors.pearl,
                fontSize: 14,
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

// ─── FavoriteBarberiaTile ─────────────────────────────────────────────────────

class FavoriteBarberiaTile extends StatelessWidget {
  final FavoriteBarberia barberia;
  final VoidCallback onTap;
  final VoidCallback? onRemove;

  const FavoriteBarberiaTile({
    super.key,
    required this.barberia,
    required this.onTap,
    this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md, vertical: AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Row(
          children: [
            // Avatar
            Container(
              width: 40, height: 40,
              decoration: BoxDecoration(
                gradient: AppColors.goldGradient,
                borderRadius: AppRadius.rSm,
              ),
              child: Center(
                child: Text(
                  barberia.name[0].toUpperCase(),
                  style: AppTypography.headingMd.copyWith(
                    color: AppColors.obsidian, fontWeight: FontWeight.w900),
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            // Info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(barberia.name, style: AppTypography.headingSm),
                  Row(
                    children: [
                      if (barberia.avgRating > 0) ...[
                        const Icon(Icons.star_rounded,
                          size: 11, color: AppColors.gold),
                        const SizedBox(width: 2),
                        Text(barberia.avgRating.toStringAsFixed(1),
                          style: AppTypography.monoSm.copyWith(
                            color: AppColors.gold, fontSize: 10)),
                        const SizedBox(width: AppSpacing.sm),
                      ],
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 5, vertical: 1),
                        decoration: BoxDecoration(
                          color: barberia.isOpen
                              ? AppColors.confirmedSubtle
                              : AppColors.errorSubtle,
                          borderRadius: AppRadius.rPill,
                        ),
                        child: Text(
                          barberia.isOpen ? 'Abierto' : 'Cerrado',
                          style: AppTypography.overline.copyWith(
                            color: barberia.isOpen
                                ? AppColors.confirmed : AppColors.error,
                            fontSize: 8,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right_rounded,
              size: 18, color: AppColors.ash),
          ],
        ),
      ),
    );
  }
}
