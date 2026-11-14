import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/dashboard_entities.dart';

// ─── MetricCard ───────────────────────────────────────────────────────────────

class MetricCard extends StatelessWidget {
  final String value;
  final String label;
  final bool isGold;
  final IconData? icon;

  const MetricCard({
    super.key,
    required this.value,
    required this.label,
    this.isGold = false,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md,
        vertical: AppSpacing.md,
      ),
      decoration: BoxDecoration(
        color: isGold ? Colors.transparent : AppColors.graphite,
        gradient: isGold
            ? LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  AppColors.gold.withOpacity(0.18),
                  AppColors.gold.withOpacity(0.06),
                ],
              )
            : null,
        borderRadius: AppRadius.rCard,
        border: Border.all(
          color: isGold
              ? AppColors.gold.withOpacity(0.35)
              : AppColors.smoke,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: isGold ? AppColors.gold : AppColors.ash),
            const SizedBox(height: 6),
          ],
          Text(
            value,
            style: AppTypography.monoLg.copyWith(
              color: isGold ? AppColors.goldLight : AppColors.white,
              fontSize: 22,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: AppTypography.overline.copyWith(
              color: AppColors.ash,
              fontSize: 9,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── MetricsStrip ─────────────────────────────────────────────────────────────

class MetricsStrip extends StatelessWidget {
  final DashboardMetrics metrics;

  const MetricsStrip({super.key, required this.metrics});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: MetricCard(
            value: metrics.appointmentsToday.toString(),
            label: 'HOY',
            isGold: true,
            icon: Icons.calendar_today_rounded,
          ).animate().fadeIn(delay: 100.ms).slideY(begin: 0.2),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: MetricCard(
            value: '\$${metrics.revenueWeek}',
            label: 'SEMANA',
            icon: Icons.payments_outlined,
          ).animate().fadeIn(delay: 180.ms).slideY(begin: 0.2),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: MetricCard(
            value: metrics.attendancePercent,
            label: 'ASIST.',
            icon: Icons.check_circle_outline_rounded,
          ).animate().fadeIn(delay: 260.ms).slideY(begin: 0.2),
        ),
      ],
    );
  }
}

// ─── AppointmentCard ──────────────────────────────────────────────────────────

class AppointmentCard extends StatelessWidget {
  final Appointment appointment;
  final VoidCallback? onMarkNoShow;
  final VoidCallback? onTap;

  const AppointmentCard({
    super.key,
    required this.appointment,
    this.onMarkNoShow,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.md,
        ),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: appointment.isPending
                ? AppColors.gold.withOpacity(0.3)
                : AppColors.smoke,
          ),
        ),
        child: Row(
          children: [
            _TimeBlock(appointment: appointment),
            const SizedBox(width: AppSpacing.md),
            Expanded(child: _AppointmentInfo(appointment: appointment)),
            _StatusIndicator(appointment: appointment, onMarkNoShow: onMarkNoShow),
          ],
        ),
      ),
    );
  }
}

class _TimeBlock extends StatelessWidget {
  final Appointment appointment;
  const _TimeBlock({required this.appointment});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 52,
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      decoration: BoxDecoration(
        color: appointment.isPending
            ? AppColors.gold.withOpacity(0.1)
            : AppColors.slate,
        borderRadius: AppRadius.rSm,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            appointment.startTimeFormatted,
            style: AppTypography.monoMd.copyWith(
              color: appointment.isPending ? AppColors.gold : AppColors.pearl,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 2),
          Text(
            '${appointment.service.durationMinutes}m',
            style: AppTypography.monoSm.copyWith(fontSize: 9),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

class _AppointmentInfo extends StatelessWidget {
  final Appointment appointment;
  const _AppointmentInfo({required this.appointment});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          appointment.client.name,
          style: AppTypography.headingSm.copyWith(fontSize: 13),
          overflow: TextOverflow.ellipsis,
        ),
        const SizedBox(height: 2),
        Text(
          '${appointment.service.name} · ${appointment.barberName}',
          style: AppTypography.bodySm,
          overflow: TextOverflow.ellipsis,
        ),
        if (appointment.depositStatus == 'PAID') ...[
          const SizedBox(height: 4),
          Row(
            children: [
              Icon(
                Icons.check_circle_rounded,
                size: 10,
                color: AppColors.confirmed,
              ),
              const SizedBox(width: 3),
              Text(
                'Depósito pagado',
                style: AppTypography.labelSm.copyWith(
                  color: AppColors.confirmed,
                  fontSize: 9,
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

class _StatusIndicator extends StatelessWidget {
  final Appointment appointment;
  final VoidCallback? onMarkNoShow;

  const _StatusIndicator({required this.appointment, this.onMarkNoShow});

  @override
  Widget build(BuildContext context) {
    return switch (appointment.status) {
      AppointmentStatus.confirmed => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8, height: 8,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.confirmed,
            ),
          ),
          const SizedBox(height: 6),
          if (onMarkNoShow != null)
            GestureDetector(
              onTap: () => _showNoShowDialog(context),
              child: Text(
                'No show',
                style: AppTypography.labelSm.copyWith(
                  color: AppColors.ash,
                  fontSize: 9,
                ),
              ),
            ),
        ],
      ),
      AppointmentStatus.pendingConfirmation => Container(
        width: 8, height: 8,
        decoration: const BoxDecoration(
          shape: BoxShape.circle,
          color: AppColors.gold,
        ),
      ),
      AppointmentStatus.completed => const Icon(
        Icons.check_rounded,
        size: 16,
        color: AppColors.confirmed,
      ),
      AppointmentStatus.noShow => const Icon(
        Icons.person_off_outlined,
        size: 16,
        color: AppColors.error,
      ),
      AppointmentStatus.cancelled => const Icon(
        Icons.cancel_outlined,
        size: 16,
        color: AppColors.ash,
      ),
    };
  }

  Future<void> _showNoShowDialog(BuildContext context) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Marcar no-show', style: AppTypography.headingMd),
        content: Text(
          '¿${appointment.client.name} no se presentó?\n\nSe retendrá el depósito si aplica.',
          style: AppTypography.bodyMd,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Cancelar',
              style: AppTypography.labelMd.copyWith(color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Confirmar',
              style: AppTypography.labelMd.copyWith(
                color: AppColors.error,
                fontWeight: FontWeight.w700,
              )),
          ),
        ],
      ),
    );
    if (confirm == true) onMarkNoShow?.call();
  }
}

// ─── DateSelector ─────────────────────────────────────────────────────────────

class DateSelector extends StatelessWidget {
  final DateTime selectedDate;
  final ValueChanged<DateTime> onDateChanged;

  const DateSelector({
    super.key,
    required this.selectedDate,
    required this.onDateChanged,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final dates = List.generate(7, (i) => now.add(Duration(days: i - 2)));

    return SizedBox(
      height: 68,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: dates.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppSpacing.sm),
        itemBuilder: (context, i) {
          final date = dates[i];
          final isSelected = _isSameDay(date, selectedDate);
          final isToday = _isSameDay(date, now);

          return GestureDetector(
            onTap: () => onDateChanged(date),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 44,
              decoration: BoxDecoration(
                gradient: isSelected ? AppColors.goldGradient : null,
                color: isSelected ? null : AppColors.graphite,
                borderRadius: AppRadius.rMd,
                border: Border.all(
                  color: isSelected
                      ? Colors.transparent
                      : isToday
                          ? AppColors.gold.withOpacity(0.4)
                          : AppColors.smoke,
                ),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    _dayName(date),
                    style: AppTypography.overline.copyWith(
                      color: isSelected ? AppColors.obsidian : AppColors.ash,
                      fontSize: 8,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    date.day.toString(),
                    style: AppTypography.monoMd.copyWith(
                      color: isSelected ? AppColors.obsidian : AppColors.white,
                      fontSize: 16,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  String _dayName(DateTime d) {
    const days = ['LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB', 'DOM'];
    return days[d.weekday - 1];
  }
}

// ─── EmptyDayWidget ───────────────────────────────────────────────────────────

class EmptyDayWidget extends StatelessWidget {
  final VoidCallback? onCreateAppointment;

  const EmptyDayWidget({super.key, this.onCreateAppointment});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 72, height: 72,
            decoration: BoxDecoration(
              color: AppColors.graphite,
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.smoke),
            ),
            child: const Icon(
              Icons.calendar_today_outlined,
              size: 28, color: AppColors.ash,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          Text('Sin citas este día', style: AppTypography.headingSm.copyWith(
            color: AppColors.silver,
          )),
          const SizedBox(height: AppSpacing.xs),
          Text('Crea la primera cita del día',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash)),
          if (onCreateAppointment != null) ...[
            const SizedBox(height: AppSpacing.lg),
            GestureDetector(
              onTap: onCreateAppointment,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.xl,
                  vertical: AppSpacing.sm + 2,
                ),
                decoration: BoxDecoration(
                  gradient: AppColors.goldGradient,
                  borderRadius: AppRadius.rButton,
                  boxShadow: AppColors.shadowGoldBtn,
                ),
                child: Text(
                  '+ Nueva cita',
                  style: AppTypography.labelLg.copyWith(
                    color: AppColors.obsidian,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ─── PendingBanner ────────────────────────────────────────────────────────────

class PendingBanner extends StatelessWidget {
  final int count;

  const PendingBanner({super.key, required this.count});

  @override
  Widget build(BuildContext context) {
    if (count == 0) return const SizedBox.shrink();

    return GoldGlowContainer(
      child: Row(
        children: [
          Container(
            width: 32, height: 32,
            decoration: BoxDecoration(
              color: AppColors.goldSubtle,
              borderRadius: AppRadius.rSm,
            ),
            child: Center(
              child: Text(
                count.toString(),
                style: AppTypography.monoMd.copyWith(
                  color: AppColors.gold,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  count == 1
                      ? '1 cita sin confirmar'
                      : '$count citas sin confirmar',
                  style: AppTypography.labelLg.copyWith(color: AppColors.white),
                ),
                Text(
                  'Esperando depósito del cliente',
                  style: AppTypography.bodySm,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
