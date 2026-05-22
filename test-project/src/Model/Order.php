<?php

namespace App\Model;

use App\Contract\ServiceInterface;
use App\Service\EmailService;
use App\Service\UserService;

class Order
{
    private int $id;
    private string $description;
    private float $total;

    public function __construct(int $id, string $description, float $total)
    {
        $this->id = $id;
        $this->description = $description;
        $this->total = $total;
    }

    public function process(ServiceInterface $processor): string
    {
        $input = "Order #{$this->id}: {$this->description} (\${$this->total})";

        if ($processor instanceof EmailService) {
            $input .= ' via email';
        } elseif ($processor instanceof UserService) {
            $input .= ' via user service';
        }

        return $processor->execute($input);
    }

    public function getId(): int
    {
        return $this->id;
    }
}
