import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../notifiers/home_owner_notifier.dart';

// ─── CRM de Clientes ──────────────────────────────────────────────────────────
// Reutiliza datos ya cargados en HomeOwnerNotifier:
//   - appointments → historial de citas por cliente
//   - loyaltyProgram → progreso de fidelización

class ClientsScreen extends ConsumerStatefulWidget {
  const ClientsScreen({super.key});

  @override
  ConsumerState<ClientsScreen> createState() => _ClientsScreenState();
}

class _ClientsScreenState extends ConsumerState<ClientsScreen> {
  final _searchCtrl = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final appointments = ref.watch(homeOwnerNotifierProvider
        .select((s) => s.appointments));
    final loyalty = ref.watch(homeOwnerNotifierProvider
        .select((s) => s.loyaltyProgram));

    // Agrupar citas por cliente
    final clients = _buildClientList(appointments, loyalty);
    final filtered = _query.isEmpty
        ? clients
        : clients.where((c) =>
            c.name.toLowerCase().contains(_query.toLowerCase()) ||
            c.phone.contains(_query)).toList();

    return Scaffold(
      backgroundColor: AppColors.obsidian,
      appBar: AppBar(
        backgroundColor: AppColors.obsidian,
        title: Text('Clientes', style: AppTypography.headingMd),
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: const Icon(Icons.arrow_back_ios_rounded,
            size: 18, color: AppColors.white),
        ),
      ),
      body: Column(
        children: [
          // Buscador
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.base, vertical: AppSpacing.sm),
            child: AppTextField(
              label: 'Buscar cliente',
              controller: _searchCtrl,
              prefixIcon: const Icon(Icons.search_rounded,
                size: 18, color: AppColors.ash),
              onChanged: (v) => setState(() => _query = v),
            ),
          ),

          // Métricas rápidas
          if (clients.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.base, vertical: AppSpacing.xs),
              child: _ClientsMetrics(clients: clients),
            ),

          // Lista
          Expanded(
            child: filtered.isEmpty
                ? _EmptyState(hasSearch: _query.isNotEmpty)
                : ListView.separated(
                    padding: AppSpacing.screen,
                    itemCount: filtered.length,
                    separatorBuilder: (_, __) =>
                        const SizedBox(height: AppSpacing.sm),
                    itemBuilder: (ctx, i) => _ClientCard(
                      client: filtered[i],
                      onTap: () => _showClientDetail(ctx, filtered[i]),
                    ).animate().fadeIn(
                      delay: Duration(milliseconds: 40 * i)),
                  ),
          ),
        ],
      ),
    );
  }

  // ─── Construir lista de clientes desde citas ────────────────────────────────

  List<_ClientSummary> _buildClientList(
    List<Appointment> appointments,
    LoyaltyProgram? loyalty,
  ) {
    final map = <String, _ClientSummary>{};

    for (final apt in appointments) {
      final key = apt.client.phone; // identificador único del cliente
      if (!map.containsKey(key)) {
        map[key] = _ClientSummary(
          id:            apt.client.id,
          name:          apt.client.name,
          phone:         apt.client.phone,
          email:         apt.client.email,
          appointments:  [],
          loyaltyVisits: 0,
        );
      }
      map[key]!.appointments.add(apt);
    }

    return map.values.toList()
      ..sort((a, b) => b.totalAppointments.compareTo(a.totalAppointments));
  }

  void _showClientDetail(BuildContext context, _ClientSummary client) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _ClientDetailSheet(client: client),
    );
  }
}

// ─── _ClientsMetrics ──────────────────────────────────────────────────────────

class _ClientsMetrics extends StatelessWidget {
  final List<_ClientSummary> clients;

  const _ClientsMetrics({super.key, required this.clients});

  @override
  Widget build(BuildContext context) {
    final total     = clients.length;
    final recurrent = clients.where((c) => c.totalAppointments > 1).length;
    final noShows   = clients.where((c) => c.noShowCount > 0).length;

    return Row(
      children: [
        Expanded(child: _MetricPill(
          label: 'Total',
          value: '$total',
          icon: Icons.people_outline_rounded,
        )),
        const SizedBox(width: AppSpacing.sm),
        Expanded(child: _MetricPill(
          label: 'Recurrentes',
          value: '$recurrent',
          icon: Icons.repeat_rounded,
          isGold: true,
        )),
        const SizedBox(width: AppSpacing.sm),
        Expanded(child: _MetricPill(
          label: 'No shows',
          value: '$noShows',
          icon: Icons.person_off_outlined,
          isRed: noShows > 0,
        )),
      ],
    );
  }
}

class _MetricPill extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final bool isGold;
  final bool isRed;

  const _MetricPill({
    required this.label,
    required this.value,
    required this.icon,
    this.isGold = false,
    this.isRed = false,
  });

  @override
  Widget build(BuildContext context) {
    final color = isRed ? AppColors.error
        : isGold ? AppColors.gold
        : AppColors.silver;

    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md, vertical: AppSpacing.sm),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Row(
        children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: AppSpacing.xs),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(value, style: AppTypography.monoMd.copyWith(
                color: color, fontSize: 15)),
              Text(label, style: AppTypography.overline.copyWith(
                color: AppColors.ash, fontSize: 8)),
            ],
          ),
        ],
      ),
    );
  }
}

// ─── _ClientCard ──────────────────────────────────────────────────────────────

class _ClientCard extends StatelessWidget {
  final _ClientSummary client;
  final VoidCallback onTap;

  const _ClientCard({super.key, required this.client, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: client.noShowCount > 0
                ? AppColors.error.withOpacity(0.25)
                : AppColors.smoke),
        ),
        child: Row(
          children: [
            // Avatar inicial
            Container(
              width: 40, height: 40,
              decoration: BoxDecoration(
                color: client.isRecurrent
                    ? AppColors.goldSubtle : AppColors.slate,
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  client.name.isNotEmpty
                      ? client.name[0].toUpperCase() : 'C',
                  style: AppTypography.headingSm.copyWith(
                    color: client.isRecurrent
                        ? AppColors.gold : AppColors.silver,
                    fontWeight: FontWeight.w700),
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            // Info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(client.name, style: AppTypography.headingSm),
                  const SizedBox(height: 2),
                  Text(client.phone,
                    style: AppTypography.bodySm),
                ],
              ),
            ),
            // Stats + chevron
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.content_cut_rounded,
                      size: 11, color: AppColors.ash),
                    const SizedBox(width: 3),
                    Text('${client.totalAppointments}',
                      style: AppTypography.monoSm.copyWith(
                        color: AppColors.pearl, fontSize: 11)),
                  ],
                ),
                if (client.noShowCount > 0) ...[
                  const SizedBox(height: 2),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.warning_amber_rounded,
                        size: 11, color: AppColors.error),
                      const SizedBox(width: 3),
                      Text('${client.noShowCount} no show',
                        style: AppTypography.overline.copyWith(
                          color: AppColors.error, fontSize: 9)),
                    ],
                  ),
                ],
              ],
            ),
            const SizedBox(width: AppSpacing.sm),
            const Icon(Icons.chevron_right_rounded,
              size: 16, color: AppColors.ash),
          ],
        ),
      ),
    );
  }
}

// ─── _ClientDetailSheet ───────────────────────────────────────────────────────

class _ClientDetailSheet extends StatelessWidget {
  final _ClientSummary client;

  const _ClientDetailSheet({super.key, required this.client});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.75,
      decoration: const BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rBottomSheet),
      child: Column(
        children: [
          // Handle + header
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40, height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill),
          ),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Row(
              children: [
                Container(
                  width: 48, height: 48,
                  decoration: BoxDecoration(
                    gradient: AppColors.goldGradient,
                    shape: BoxShape.circle),
                  child: Center(
                    child: Text(
                      client.name.isNotEmpty
                          ? client.name[0].toUpperCase() : 'C',
                      style: AppTypography.headingLg.copyWith(
                        color: AppColors.obsidian,
                        fontWeight: FontWeight.w900),
                    ),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(client.name, style: AppTypography.headingMd),
                      Text(client.phone, style: AppTypography.bodySm),
                      if (client.email != null)
                        Text(client.email!, style: AppTypography.bodySm),
                    ],
                  ),
                ),
                // Botón llamar
                GestureDetector(
                  onTap: () => HapticFeedback.lightImpact(),
                  child: Container(
                    width: 40, height: 40,
                    decoration: BoxDecoration(
                      color: AppColors.goldSubtle,
                      borderRadius: AppRadius.rSm,
                      border: Border.all(
                        color: AppColors.gold.withOpacity(0.3)),
                    ),
                    child: const Icon(Icons.phone_outlined,
                      size: 18, color: AppColors.gold),
                  ),
                ),
              ],
            ),
          ),
          Container(height: 1, color: AppColors.smoke),

          // Stats
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Row(
              children: [
                _StatBox(label: 'Citas', value: '${client.totalAppointments}',
                  icon: Icons.content_cut_rounded),
                const SizedBox(width: AppSpacing.sm),
                _StatBox(label: 'Completadas', value: '${client.completedCount}',
                  icon: Icons.check_circle_outline_rounded,
                  color: AppColors.confirmed),
                const SizedBox(width: AppSpacing.sm),
                _StatBox(label: 'No shows', value: '${client.noShowCount}',
                  icon: Icons.person_off_outlined,
                  color: client.noShowCount > 0
                      ? AppColors.error : AppColors.ash),
              ],
            ),
          ),

          // Historial de citas
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.base),
            child: Row(
              children: [
                Text('HISTORIAL', style: AppTypography.overline.copyWith(
                  color: AppColors.ash)),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.sm),

          Expanded(
            child: client.appointments.isEmpty
                ? Center(
                    child: Text('Sin citas registradas',
                      style: AppTypography.bodyMd.copyWith(
                        color: AppColors.ash)))
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.base),
                    itemCount: client.appointments.length,
                    itemBuilder: (_, i) {
                      final apt = client.appointments[i];
                      return _AppointmentHistoryRow(appointment: apt);
                    },
                  ),
          ),
          SizedBox(height: MediaQuery.of(context).padding.bottom + AppSpacing.md),
        ],
      ),
    ).animate().slideY(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic);
  }
}

class _StatBox extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color? color;

  const _StatBox({
    required this.label,
    required this.value,
    required this.icon,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.pearl;
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.sm + 2),
        decoration: BoxDecoration(
          color: AppColors.slate,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke)),
        child: Column(
          children: [
            Icon(icon, size: 18, color: c),
            const SizedBox(height: 4),
            Text(value, style: AppTypography.monoMd.copyWith(
              color: c, fontSize: 18)),
            Text(label, style: AppTypography.overline.copyWith(
              color: AppColors.ash, fontSize: 9)),
          ],
        ),
      ),
    );
  }
}

class _AppointmentHistoryRow extends StatelessWidget {
  final Appointment appointment;

  const _AppointmentHistoryRow({required this.appointment});

  @override
  Widget build(BuildContext context) {
    final isNoShow   = appointment.status == AppointmentStatus.noShow;
    final isCompleted = appointment.status == AppointmentStatus.completed;

    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md, vertical: AppSpacing.sm + 2),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke)),
      child: Row(
        children: [
          // Fecha
          Text(
            _formatDate(appointment.startTime),
            style: AppTypography.monoSm.copyWith(
              color: AppColors.silver, fontSize: 11)),
          const SizedBox(width: AppSpacing.md),
          // Servicio
          Expanded(
            child: Text(
              appointment.service.name,
              style: AppTypography.bodySm.copyWith(
                color: AppColors.pearl),
              overflow: TextOverflow.ellipsis),
          ),
          // Estado
          Container(
            width: 8, height: 8,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: isNoShow ? AppColors.error
                  : isCompleted ? AppColors.confirmed
                  : AppColors.gold),
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime dt) {
    const months = ['ene','feb','mar','abr','may','jun',
                    'jul','ago','sep','oct','nov','dic'];
    return '${dt.day} ${months[dt.month - 1]} ${dt.year}';
  }
}

class _EmptyState extends StatelessWidget {
  final bool hasSearch;
  const _EmptyState({required this.hasSearch});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.people_outline_rounded,
            size: 40, color: AppColors.ash),
          const SizedBox(height: AppSpacing.md),
          Text(
            hasSearch ? 'Sin resultados' : 'Sin clientes aún',
            style: AppTypography.headingSm.copyWith(color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text(
            hasSearch
                ? 'Prueba con otro nombre o teléfono'
                : 'Los clientes aparecen cuando completan su primera cita',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash),
            textAlign: TextAlign.center),
        ],
      ),
    );
  }
}

// ─── Model local ─────────────────────────────────────────────────────────────

class _ClientSummary {
  final String id;
  final String name;
  final String phone;
  final String? email;
  final List<Appointment> appointments;
  int loyaltyVisits;

  _ClientSummary({
    required this.id,
    required this.name,
    required this.phone,
    this.email,
    required this.appointments,
    required this.loyaltyVisits,
  });

  int get totalAppointments => appointments.length;

  int get completedCount => appointments
      .where((a) => a.status == AppointmentStatus.completed).length;

  int get noShowCount => appointments
      .where((a) => a.status == AppointmentStatus.noShow).length;

  bool get isRecurrent => totalAppointments > 1;
}
